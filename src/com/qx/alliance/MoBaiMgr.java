package com.qx.alliance;

import java.util.Date;
import java.util.List;

import log.ActLog;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qxmobile.protobuf.ErrorMessageProtos.ErrorMessage;
import qxmobile.protobuf.Explore.Award;
import qxmobile.protobuf.Explore.ExploreAwardsInfo;
import qxmobile.protobuf.MoBaiProto.MoBaiInfo;
import qxmobile.protobuf.MoBaiProto.MoBaiReq;

import com.google.protobuf.MessageLite.Builder;
import com.manu.dynasty.base.TempletService;
import com.manu.dynasty.store.MemcachedCRUD;
import com.manu.dynasty.template.AwardTemp;
import com.manu.dynasty.template.CanShu;
import com.manu.dynasty.template.LianmengMobai;
import com.manu.dynasty.util.DateUtils;
import com.manu.network.SessionAttKey;
import com.opensymphony.util.DataUtil;
import com.qx.account.FunctionOpenMgr;
import com.qx.award.AwardMgr;
import com.qx.bag.Bag;
import com.qx.bag.BagGrid;
import com.qx.bag.BagMgr;
import com.qx.event.ED;
import com.qx.event.Event;
import com.qx.event.EventMgr;
import com.qx.event.EventProc;
import com.qx.junzhu.JunZhu;
import com.qx.junzhu.JunZhuMgr;
import com.qx.persistent.HibernateUtil;
import com.qx.purchase.PurchaseMgr;
import com.qx.task.DailyTaskCondition;
import com.qx.task.DailyTaskConstants;
import com.qx.timeworker.FunctionID;
import com.qx.vip.VipData;
import com.qx.vip.VipMgr;
import com.qx.yuanbao.YBType;
import com.qx.yuanbao.YuanBaoMgr;

public class MoBaiMgr extends EventProc{
	public static Logger log = LoggerFactory.getLogger(MoBaiMgr.class);
	public static String moBaiBuffCnt = "moBaiBuffCnt#";
	public static Date today;

	public MoBaiMgr() {

	}

	public void sendMoBaiInfo(int id, IoSession session, Builder builder) {
		sendMoBaiInfo(id, session, builder, null);
	}

	public void sendMoBaiInfo(int id, IoSession session, Builder builder,
			qxmobile.protobuf.Explore.ExploreAwardsInfo.Builder list) {
		Long jzId = (Long) session.getAttribute(SessionAttKey.junZhuId);
		if (jzId == null) {
			return;
		}
		JunZhu jz = JunZhuMgr.inst.getJunZhu(session);
		if (jz == null) {
			return;
		}
		MoBaiBean bean = HibernateUtil.find(MoBaiBean.class, jzId);
		AlliancePlayer member = HibernateUtil.find(AlliancePlayer.class, jzId);
		if (member == null) {
			sendError(id, session, "您不在联盟中。");
			return;
		}
		MoBaiInfo.Builder ret = MoBaiInfo.newBuilder();
		if (bean == null) {
			ret.setTongBiOpen(true);
			ret.setYuanBaoOpen(true);
			ret.setYuOpen(true);
		} else {
			// change 20150901
			ret.setTongBiOpen(DateUtils.isTimeToReset(bean.tongBiTime, CanShu.REFRESHTIME_PURCHASE));
			ret.setYuanBaoOpen(DateUtils.isTimeToReset(bean.yuanBaoTime, CanShu.REFRESHTIME_PURCHASE));
			int vipAddTimes = VipMgr.INSTANCE.getValueByVipLevel(jz.vipLevel,
					VipData.yujueDuihuan);
			ret.setYuOpen(DateUtils.isTimeToReset(bean.yuTime, CanShu.REFRESHTIME_PURCHASE)
					|| bean.yuTimes < vipAddTimes);
		}
		LianmengMobai tbConf = getConf(1);
		LianmengMobai ybConf = getConf(2);
		if (tbConf == null || ybConf == null) {
			sendError(0, session, "今日暂停膜拜。");
			return;
		}
		ret.setTbPay(tbConf.needNum);
		ret.setTbGain(Integer.valueOf(tbConf.tili));
		ret.setYbPay(ybConf.needNum);
		ret.setYbGain(Integer.valueOf(tbConf.tili));
		LMMoBaiInfo lmmbInfo = (LMMoBaiInfo) MemcachedCRUD.getMemCachedClient()
				.get(moBaiBuffCnt+member.lianMengId);
		long cnt;
		today = new Date();
		if (lmmbInfo == null) {// 未找到联盟的勠力同心等级信息,设置为0
			cnt = 0;
		} else {
			// change 20150901
			if(DateUtils.isTimeToReset(lmmbInfo.getLastDate(), CanShu.REFRESHTIME_PURCHASE)){
				cnt = 0;
			} else {
				cnt = lmmbInfo.getBuffLevel();
			}
		}
		ret.setBuffCount((int) cnt);
		if (list != null) {
			ret.setMobaiGain(list);
		}
		session.write(ret.build());
		log.info("发送膜拜信息给{}，buff cnt {}", jzId, cnt);
	}

	public synchronized void moBai(int id, IoSession session, Builder builder) {
		Long jzId = (Long) session.getAttribute(SessionAttKey.junZhuId);
		if (jzId == null) {
			return;
		}
		AlliancePlayer member = HibernateUtil.find(AlliancePlayer.class, jzId);
		if (member == null || member.lianMengId <= 0) {
			sendError(id, session, "您不在联盟中。");
			return;
		}
		MoBaiBean bean = HibernateUtil.find(MoBaiBean.class, jzId);
		if (bean == null) {// 没有膜拜过
			bean = new MoBaiBean();
			bean.junZhuId = member.junzhuId;
		}
		MoBaiReq.Builder req = (qxmobile.protobuf.MoBaiProto.MoBaiReq.Builder) builder;
		int cmd = req.getCmd();
		switch (cmd) {// 1 铜币膜拜；2 元宝膜拜； 3 玉膜拜
		case 1:
			tongBiDo(session, bean, member);
			break;
		case 2:
			yuanBaoDo(id, session, bean, member);
			break;
		case 3:
			yuDo(id, session, bean, member);
			break;
		default:
			sendError(cmd, session, "膜拜类型错误：" + cmd);
			break;
		}
	}

	protected void yuDo(int cmd, IoSession session, MoBaiBean bean,
			AlliancePlayer member) {
		// change 20150901
		if(DateUtils.isTimeToReset(bean.yuTime, CanShu.REFRESHTIME_PURCHASE)){
			bean.yuTimes = 0;
		}
		JunZhu jz = JunZhuMgr.inst.getJunZhu(session);
		if (jz == null) {
			return;
		}
		int vipAddTimes = VipMgr.INSTANCE.getValueByVipLevel(jz.vipLevel,
				VipData.yujueDuihuan);
		if (bean.yuTimes >= vipAddTimes) {
			log.info("{}玉膜拜次数受限{} vip 次数{}", jz.name, bean.yuTimes, vipAddTimes);
			return;
		}
		LianmengMobai conf = getConf(3);
		if (conf == null) {
			log.error("没有找到配置 {}", 3);
			return;
		}
		boolean isPermit = VipMgr.INSTANCE.isVipPermit(VipData.dingLi_moBai,
				jz.vipLevel);
		if (!isPermit) {
			log.error("VIP等级不够，不能进行元宝膜拜");
			sendError(cmd, session, "VIP等级不够，不能进行元宝膜拜");
			return;
		}
		int ids[] = { 950001, 950002, 950003, 950004, 950005 };
		Bag<BagGrid> bag = BagMgr.inst.loadBag(member.junzhuId);
		for (int yuId : ids) {
			int cnt = BagMgr.inst.getItemCount(bag, yuId);
			if (cnt <= 0) {
				sendError(0, session, "缺少所需的物品" + yuId);
				return;
			}
		}
		JSONArray spend = new JSONArray();
		for (int yuId : ids) {
			JSONObject jo = new JSONObject();
			jo.put("name", ""+yuId);
			jo.put("num", 1);
			spend.add(jo);
			BagMgr.inst.removeItem(bag, yuId, 1, "膜拜",jz.level);
		}
		today = new Date();
		BagMgr.inst.sendBagInfo(0, session, null);
		bean.yuTime = today;
		bean.yuTimes += 1;

		updateMobaiLevel(member.lianMengId, conf.buffNum, today);

		// MemcachedCRUD.getMemCachedClient().addOrIncr(moBaiBuffCnt+member.lianMengId,
		// conf.buffNum);
		HibernateUtil.save(bean);
		ExploreAwardsInfo.Builder list = ExploreAwardsInfo.newBuilder();
		{// 计算奖励
			List<AwardTemp> awardList = PurchaseMgr.inst
					.jiangLi2award(conf.award);
			list.setType(0);
			for (AwardTemp award : awardList) {
				Award.Builder ab = Award.newBuilder();
				ab.setItemId(award.getItemId());
				ab.setItemType(award.getItemType());
				ab.setItemNumber(award.getItemNum());
				ab.setIsQuality(1);
				AwardMgr.inst.giveReward(session, award, jz);
				log.info("{}玉膜拜获得奖励 type:{} id:{} num:{}", bean.junZhuId,
						ab.getItemType(), ab.getItemId(), ab.getItemNumber());
				list.addAwardsList(ab.build());
			}
			BagMgr.inst.sendBagInfo(0, session, null);
		}
		log.info("{}玉膜拜", bean.junZhuId);
		ActLog.log.Worship(jz.id, jz.name, ActLog.vopenid, "3", spend);
		member.gongXian += conf.gongxian;
		HibernateUtil.save(member);
		JunZhuMgr.inst.updateTiLi(jz, conf.tili, "玉膜拜");
		HibernateUtil.save(jz);
		JunZhuMgr.inst.sendMainInfo(session);
		sendMoBaiInfo(0, session, null, list);
		AllianceMgr.inst.changeGongXianRecord(jz.id, conf.gongxian);
		// 每日任务：膜拜
		EventMgr.addEvent(ED.DAILY_TASK_PROCESS, new DailyTaskCondition(
				jz.id, DailyTaskConstants.moBai, 1));
		// 主线任务: 任意膜拜1次（免费  元宝）的都算完成任务 20190916
		EventMgr.addEvent(ED.mobai , new Object[] { jz.id});
		EventMgr.addEvent(ED.YU_MO_BAI, new Object[]{jz});
	}

	protected boolean timeFail(IoSession session, Date time) {
		// change 20150901
		if (DateUtils.isTimeToReset(time, CanShu.REFRESHTIME_PURCHASE)) {
			return false;
		}else{
			sendError(0, session, "今日已膜拜！");
			return true;
		}
	}

	/**
	 * @Description: 更新联盟勠力同心等级
	 * @param lmId
	 * @param buffNum
	 *            增加的buff层数
	 * @param time
	 */
	protected void updateMobaiLevel(int lmId, int buffNum, Date time) {
		LMMoBaiInfo lmmbInfo = (LMMoBaiInfo) MemcachedCRUD.getMemCachedClient()
				.get(moBaiBuffCnt+lmId);
		if (lmmbInfo == null) {// 如果没有该联盟勠力同心信息，初始化勠力同心信息
			LMMoBaiInfo temInfo = new LMMoBaiInfo();
			temInfo.setLastDate(time);
			temInfo.setBuffLevel(buffNum);
			MemcachedCRUD.getMemCachedClient().add(moBaiBuffCnt+lmId,
					temInfo);
		} else {
			// change 20150901
			if (DateUtils.isTimeToReset(lmmbInfo.getLastDate(), CanShu.REFRESHTIME_PURCHASE)) {
				lmmbInfo.setLastDate(time);
				lmmbInfo.setBuffLevel(buffNum);
			} else {
				lmmbInfo.setLastDate(time);
				lmmbInfo.setBuffLevel(lmmbInfo.getBuffLevel() + buffNum);
			}
			MemcachedCRUD.getMemCachedClient().replace(moBaiBuffCnt+lmId,
					lmmbInfo);
		}
	}

	protected void yuanBaoDo(int cmd, IoSession session, MoBaiBean bean,
			AlliancePlayer member) {
		if (timeFail(session, bean.yuanBaoTime)) {
			return;
		}

		JunZhu jz = JunZhuMgr.inst.getJunZhu(session);
		if (jz == null) {
			return;
		}
		LianmengMobai conf = getConf(2);
		if (conf == null) {
			log.error("没有找到配置 {}", 2);
			return;
		}
		boolean isPermit = VipMgr.INSTANCE.isVipPermit(VipData.qianCheng_moBai,
				jz.vipLevel);
		if (!isPermit) {
			log.error("VIP等级不够，不能进行元宝膜拜");
			sendError(cmd, session, "VIP等级不够，不能进行元宝膜拜");
			return;
		}
		if (jz.yuanBao < conf.needNum || conf.needNum <= 0) {
			sendError(0, session, "您的元宝不足。");
			return;
		}
		// jz.yuanBao-=conf.needNum;
		YuanBaoMgr.inst.diff(jz, -conf.needNum, 0, conf.needNum,
				YBType.YB_LIANMENG_MOBAI, "联盟膜拜");
		JunZhuMgr.inst.updateTiLi(jz, conf.tili, "联盟膜拜");
		HibernateUtil.save(jz);
		log.info("膜拜扣除{}:{}元宝{}", jz.id, jz.name, conf.needNum);
		JunZhuMgr.inst.sendMainInfo(session);

		today = new Date();
		bean.yuanBaoTime = today;
		HibernateUtil.save(bean);

		updateMobaiLevel(member.lianMengId, conf.buffNum, today);
		// MemcachedCRUD.getMemCachedClient().addOrIncr(moBaiBuffCnt+member.lianMengId,
		// conf.buffNum);
		log.info("{}元宝膜拜", bean.junZhuId);
		JSONArray spend = new JSONArray();
		JSONObject jo = new JSONObject();
		jo.put("name", "元宝");
		jo.put("num", conf.needNum);
		spend.add(jo);
		ActLog.log.Worship(jz.id, jz.name, ActLog.vopenid, "2", spend);
		member.gongXian += conf.gongxian;
		HibernateUtil.save(member);
		sendMoBaiInfo(0, session, null);
		AllianceMgr.inst.changeGongXianRecord(jz.id, conf.gongxian);
		// 每日任务：膜拜
		EventMgr.addEvent(ED.DAILY_TASK_PROCESS, new DailyTaskCondition(
				jz.id, DailyTaskConstants.moBai, 1));
		// 主线任务: 任意膜拜1次（免费  元宝）的都算完成任务 20190916
		EventMgr.addEvent(ED.mobai , new Object[] { jz.id});
	}

	protected void tongBiDo(IoSession session, MoBaiBean bean,
			AlliancePlayer member) {
		if (timeFail(session, bean.tongBiTime)) {
			return;
		}
		JunZhu jz = JunZhuMgr.inst.getJunZhu(session);
		if (jz == null) {
			return;
		}
		LianmengMobai conf = getConf(1);
		if (conf == null) {
			log.error("没有找到配置 {}", 1);
			return;
		}
		if (jz.tongBi < conf.needNum || conf.needNum <= 0) {
			sendError(0, session, "您的铜币不足。");
			return;
		}
		jz.tongBi -= conf.needNum;
		JunZhuMgr.inst.updateTiLi(jz, conf.tili, "铜币膜拜");
		HibernateUtil.save(jz);
		log.info("膜拜扣除{}:{}铜币{}", jz.id, jz.name, conf.needNum);
		JSONArray spend = new JSONArray();
		JSONObject jo = new JSONObject();
		jo.put("name", "铜币");
		jo.put("num", conf.needNum);
		spend.add(jo);
		ActLog.log.Worship(jz.id, jz.name, ActLog.vopenid, "1", spend);
		JunZhuMgr.inst.sendMainInfo(session);

		today = new Date();
		bean.tongBiTime = today;
		HibernateUtil.save(bean);

		updateMobaiLevel(member.lianMengId, conf.buffNum, today);
		// MemcachedCRUD.getMemCachedClient().addOrIncr(moBaiBuffCnt+member.lianMengId,
		// conf.buffNum);
		log.info("{}铜币膜拜", bean.junZhuId);
		member.gongXian += conf.gongxian;
		HibernateUtil.save(member);
		sendMoBaiInfo(0, session, null);
		AllianceMgr.inst.changeGongXianRecord(jz.id, conf.gongxian);
		// 每日任务：膜拜
		EventMgr.addEvent(ED.DAILY_TASK_PROCESS, new DailyTaskCondition(
				jz.id, DailyTaskConstants.moBai, 1));
		// 主线任务: 任意膜拜1次（免费  元宝）的都算完成任务 20190916
		EventMgr.addEvent(ED.mobai , new Object[] { jz.id});
	}

	public void sendError(int cmd, IoSession session, String msg) {
		if (session == null) {
			log.warn("session is null: {}", msg);
			return;
		}
		ErrorMessage.Builder test = ErrorMessage.newBuilder();
		test.setErrorCode(cmd);
		test.setErrorDesc(msg);
		session.write(test.build());
	}

	public LianmengMobai getConf(int idx) {
		idx -= 1;
		List<LianmengMobai> confList = TempletService
				.listAll(LianmengMobai.class.getSimpleName());
		if (confList == null || confList.size() <= idx) {
			return null;
		}
		return confList.get(idx);
	}

	public void isCanTongBiMoBai(JunZhu jz, IoSession session){
		MoBaiBean bean = HibernateUtil.find(MoBaiBean.class, jz.id);
		if(bean != null){
			if(bean.tongBiTime != null){
				if(DateUtils.isSameDay(bean.tongBiTime)){
					return;
				}
			}
		}
		LianmengMobai conf = getConf(1);
		if (conf == null) {
			log.error("没有找到配置 {}", 1);
			return;
		}
		if (jz.tongBi < conf.needNum || conf.needNum <= 0) {
			return;
		}
		// 可以铜币膜拜
		FunctionID.pushCanShangjiao(jz.id, session, FunctionID.tongBiMoBai);
		log.info("定时刷新铜币膜拜完成");
	}

	@Override
	public void proc(Event evt) {
		switch (evt.id) {
		case ED.REFRESH_TIME_WORK:
			IoSession session=(IoSession) evt.param;
			if(session==null){
				log.error(" 定时刷新是否可以铜币膜拜，session为null");
				break;
			}
			JunZhu jz = JunZhuMgr.inst.getJunZhu(session);
			if(jz==null){
				log.error("定时刷新是否可以铜币膜拜错误，JunZhu为null");
				break;
			}
			boolean isOpen=FunctionOpenMgr.inst.isFunctionOpen(FunctionID.tongBiMoBai, jz.id, jz.level);
			if(!isOpen){
				log.info("君主：{}--铜币膜拜：{}的功能---未开启,不推送",jz.id,FunctionID.tongBiMoBai);
				break;
			}
			isCanTongBiMoBai(jz, session);
			break;
		default:
			log.error("错误事件参数",evt.id);
			break;
		}
		
	}

	@Override
	protected void doReg() {
		EventMgr.regist(ED.REFRESH_TIME_WORK, this);
	}
}