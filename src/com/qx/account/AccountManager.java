package com.qx.account;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import log.CunLiangLog;
import log.OurLog;

import org.apache.mina.core.session.IoSession;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qxmobile.protobuf.ZhangHao.CreateRoleRequest;
import qxmobile.protobuf.ZhangHao.CreateRoleResponse;
import qxmobile.protobuf.ZhangHao.LoginReq;
import qxmobile.protobuf.ZhangHao.LoginRet;
import qxmobile.protobuf.ZhangHao.RegRet;
import qxmobile.protobuf.ZhangHao.RoleNameRequest;
import qxmobile.protobuf.ZhangHao.RoleNameResponse;

import com.google.protobuf.MessageLite.Builder;
import com.manu.dynasty.base.TempletService;
import com.manu.dynasty.boot.GameServer;
import com.manu.dynasty.store.MemcachedCRUD;
import com.manu.dynasty.store.Redis;
import com.manu.dynasty.template.AwardTemp;
import com.manu.dynasty.template.JiNengPeiYang;
import com.manu.dynasty.template.Jiangli;
import com.manu.dynasty.template.NameKu;
import com.manu.dynasty.template.ZhuceRenwu;
import com.manu.dynasty.util.DateUtils;
import com.manu.network.BigSwitch;
import com.manu.network.IOHandlerImpl;
import com.manu.network.PD;
import com.manu.network.SessionAttKey;
import com.qx.alliance.AllianceBean;
import com.qx.alliance.AllianceBeanDao;
import com.qx.alliance.AllianceMgr;
import com.qx.alliance.AlliancePlayer;
import com.qx.award.AwardMgr;
import com.qx.bag.Bag;
import com.qx.bag.BagGrid;
import com.qx.bag.BagMgr;
import com.qx.event.ED;
import com.qx.event.EventMgr;
import com.qx.gm.role.GMRoleMgr;
import com.qx.guojia.GuoJiaMgr;
import com.qx.http.CreateJunZhuSer;
import com.qx.http.LoginServ;
import com.qx.junzhu.ChengHaoBean;
import com.qx.junzhu.ChengHaoDao;
import com.qx.junzhu.JunZhu;
import com.qx.junzhu.JunZhuMgr;
import com.qx.junzhu.PlayerTime;
import com.qx.junzhu.PrepareJz;
import com.qx.junzhu.TalentAttr;
import com.qx.mibao.MiBaoDB;
import com.qx.mibao.MiBaoDao;
import com.qx.mibao.MibaoMgr;
import com.qx.persistent.Cache;
import com.qx.persistent.HibernateUtil;
import com.qx.purchase.PurchaseMgr;
import com.qx.pve.PveGuanQiaMgr;
import com.qx.pve.PveRecord;
import com.qx.pve.PveRecordDao;
import com.qx.pvp.PVPConstant;
import com.qx.task.GameTaskMgr;
import com.qx.util.DelayedSQLMgr;
import com.qx.util.RandomUtil;
import com.qx.world.PosInfo;
import com.qx.yabiao.YaBiaoHuoDongMgr;

/**
 * 重构需求：改造成多线程处理。
 * 
 * @author 康建虎
 * 
 */
public class AccountManager {
	public static final int ERR_NAME_EXISTS = -2;
	public static final int ERR_NAME_WRONG = -2;
	public static final int NAME_LENGTH_JUNZHU_MAX = 7;
	public static Logger log = LoggerFactory.getLogger(AccountManager.class);
	/**
	 * key->jzId , value IoSession
	 */
	public static Map<Long, IoSession> sessionMap = new ConcurrentHashMap<Long, IoSession>();
	/** 角色配置信息 **/
	public Map<Integer, ZhuceRenwu> roleInfoList;
	public List<String> sensitiveWord;
//	public List<String> illegalityName;

	/** 角色名称库-Map<性别,list> **/
	public Map<Integer, List<NameKu>> roleNameMap;

	public static AccountManager inst;

	public static int guoJiaFirstId = 1;
	public static int guoJiaLastId = 7;

	/**
	 * @Fields CACHE_ONLINETIME : 2015年7月4日 11：10 GM统计在线时间，Redis记录在线时间，下线累加到数据库
	 */
	public static final String CACHE_ONLINETIME = "GMOnlinetime:id:";

	public AccountManager() {
		inst = this;
		initData();
	}

	public void shutdown() {
	}

	public void initData() {
		initRoleInfo();
		initSensitiveWordAndIllegalityName();
//		initRoleNameList();
		FunctionOpenMgr.inst.init();
	}

	public void initRoleNameList() {
		List<NameKu> dbList = HibernateUtil.list(NameKu.class, "");
		Map<Integer, List<NameKu>> roleNameMap = new HashMap<Integer, List<NameKu>>();
		for (NameKu nameKu : dbList) {
			List<NameKu> ramList = roleNameMap.get(nameKu.sex);
			if (ramList == null) {
				ramList = new ArrayList<NameKu>();
				roleNameMap.put(nameKu.sex, ramList);
			}
			ramList.add(nameKu);
		}
		this.roleNameMap = roleNameMap;
	}

	public void initSensitiveWordAndIllegalityName() {
		String headPath = AccountManager.class.getClassLoader()
				.getResource("/").getPath();
		/*SAXReader reader = new SAXReader();
		Document document = null;
		try {
			document = reader.read(new File(headPath + "/syspara.xml"));
			Element rootEle = document.getRootElement();
			Iterator<Element> iterator = rootEle
					.elementIterator("IllegalityName");
			List<String> illegalityName = new ArrayList<String>();
			while (iterator.hasNext()) {
				Element illegalEle = iterator.next();
				String value = illegalEle.attributeValue("word");
				illegalityName.add(value);
			}
			this.illegalityName = illegalityName;

			List<String> sensitiveWord = new ArrayList<String>();
			iterator = rootEle.elementIterator("SensitiveWord");
			while (iterator.hasNext()) {
				Element sensitiveEle = iterator.next();
				String value = sensitiveEle.attributeValue("word");
				sensitiveWord.add(value);
			}
			this.sensitiveWord = sensitiveWord;
		} catch (DocumentException e) {
			e.printStackTrace();
		}*/
		//2016年3月17日16:01:33 孙晓楠给的文本文件
		List<String> sensitiveWord = new ArrayList<String>();
		File f = new File(headPath + "/PingBiCi.txt");
		try{
			BufferedInputStream br = 
					new BufferedInputStream(new FileInputStream(f));
			BufferedReader r = new BufferedReader(new InputStreamReader(br, "UTF-8"));
			String line = r.readLine();
			String[] arr = line.split("[\"“]");
			for(String s : arr){
				s = s.trim();
				if(s.isEmpty())continue;
				if(s.equals(","))continue;
//				System.out.println(s);
				sensitiveWord.add(s);
			}
			this.sensitiveWord = sensitiveWord;
			log.info("size {}", sensitiveWord.size());
			r.close();
			br.close();
//			System.exit(0);
		}catch(Exception e){
			log.error("载入屏蔽词出错",e);
		}
	}

	public List<String> getSensitiveWord() {
		return sensitiveWord;
	}

//	public List<String> getIllegalityName() {
//		return illegalityName;
//	}

	public void initRoleInfo() {
		List<ZhuceRenwu> list = TempletService.listAll(ZhuceRenwu.class
				.getSimpleName());
		Map<Integer, ZhuceRenwu> roleInfoList = new HashMap<Integer, ZhuceRenwu>();
		if (list == null) {
			log.error("角色信息 配置文件加载错误");
			return;
		}
		for (ZhuceRenwu renwu : list) {
			roleInfoList.put(renwu.id, renwu);
		}
		this.roleInfoList = roleInfoList;
	}

	public void error(IoSession session, int errNameExists, String string) {
		qxmobile.protobuf.ZhangHao.RegRet.Builder ret = RegRet.newBuilder();
		ret.setUid(errNameExists);
		ret.setName(string);
		session.write(ret.build());
	}

	public void login0(final int id, final IoSession session,
			final Builder builder) {
				login(id, session, builder);
	}

	public void login(int id, IoSession session, Builder builder) {
		LoginReq.Builder b = (qxmobile.protobuf.ZhangHao.LoginReq.Builder) builder;
		if(b==null){
			log.error("login消息是null");
			return;
		}
		String name = b.getName();
		//从router那里检查该账户是否已登录。
		session.setAttribute("needSendChengHao", Boolean.TRUE);
		new LoginServ(session,name).start();
	}
	@SuppressWarnings("deprecation")
	public void loginBackFromRouter(String accName, IoSession session, long accId){
		final long junZhuId = accId * 1000
				+ GameServer.serverId;
		LoginRet.Builder ret = LoginRet.newBuilder();
		// TODO 需要前台配合弹窗提示并返回登录
		// 2015年7月3日 15:47 检查账号是否处于封停状态，封停则禁止登陆
		if (GMRoleMgr.checkGMFengting(junZhuId)) {
			String times = Redis.getInstance().get(
					GMRoleMgr.CACHE_ROLE_BAN_USER + junZhuId);
			ret.setCode(3);
			ret.setMsg("账号已封停到"
					+ new Date(Long.valueOf(times))
							.toLocaleString() + "");
			session.write(ret.build());
			return;
		}

		// 保存登录的服务器
		int serverId = GameServer.serverId;
		if (serverId == 0) {
			log.error("保存登录服务器失败，未找到服务器id配置");
		}
		// 将accId 全部替换为junZhuId
		// 保存帐号id
		session.setAttribute(SessionAttKey.junZhuId, junZhuId);
		session.setAttribute(SessionAttKey.jzKey, SessionAttKey.jzKey+"#"+junZhuId);
		JunZhu junZhu = HibernateUtil.find(JunZhu.class, junZhuId);
		ret.addAllOpenFunctionID(FunctionOpenMgr.initIds);
//		String ip = getIp(session);
		if (junZhu == null) {
			ret.setCode(2);//无君主
			/*PrepareJz preJz = HibernateUtil.find(PrepareJz.class, junZhuId);
			if(preJz != null){
				log.info("{} 重新进入创建君主",preJz);
				ret.setCode(2016);//之前已进入创建角色界面
			}*/
			if(Redis.instance.sexist("PrepareJz", String.valueOf(junZhuId))){
				log.info("{} 重新进入创建君主",junZhuId);
				ret.setCode(2016);//之前已进入创建角色界面
			}
			int guojia = 1;//GuoJiaMgr.getLeastCountGuoJiaId();
			ret.setGuoJiaId(guojia);
			ret.setSerTime((new Date()).getHours());
			session.setAttribute(SessionAttKey.NEW_PLAYER_GUOJIA_TUIJIAN, guojia);
			log.info("{} ,登录成功，并进入创建角色", accName);
		} else {
			ret.setCode(1);
			AlliancePlayer member = HibernateUtil.find(AlliancePlayer.class, junZhu.id);
			session.setAttribute(SessionAttKey.LM_INFO, member);
			if(member != null && member.lianMengId > 0) {
				AllianceBean alliance = AllianceBeanDao.inst.getAllianceBean(member.lianMengId);
				if(alliance != null){
					session.setAttribute(SessionAttKey.LM_ZHIWU, member == null ? 0 : member.title);
					session.setAttribute(SessionAttKey.LM_NAME, alliance.name);
					ret.setCode(100);	//表示有联盟
				}
			} else {
				//ret.setCode(1);
			}
			ret.setRoleId(junZhu.roleId);
			// 所属国家
			ret.setGuoJiaId(junZhu.guoJiaId);

			PosInfo pos = HibernateUtil.find(PosInfo.class, junZhuId);
			if (pos == null) {
				ret.setX(0);
				ret.setY(0);
				ret.setZ(0);
			} else {
				ret.setX(pos.x);
				ret.setY(pos.y);
				ret.setZ(pos.z);
			}
			log.info("登录成功 {} ， 已有角色，联盟 {}", accName, ret.getCode() == 100 ? "有"
					: "无");
			FunctionOpenMgr.inst.fillOther(ret, junZhu.level, junZhu.id);
			EventMgr.addEvent(junZhuId,ED.JUNZHU_LOGIN, junZhu);
			EventMgr.addEvent(junZhuId,ED.CHECK_EMAIL, Long.valueOf(junZhuId));
			ret.setSerTime((new Date()).getHours());

			//TODO 目前阶段等级排行榜不完整，加入  登录时君主等级榜刷新，优化的时候可以删掉删掉 删掉 删掉
			EventMgr.addEvent(junZhuId,ED.JUNZHU_LEVEL_RANK_REFRESH, junZhu);
		}

		ret.setMsg("登录成功");
		String serverTime=	DateUtils.datetime2Text(new Date());
		ret.setServerTime(serverTime);
		PlayerTime playerTime = HibernateUtil.find(PlayerTime.class, junZhuId);
		if(playerTime != null&&playerTime.createRoleTime!=null) {
			String createRoleTime=	DateUtils.datetime2Text(playerTime.createRoleTime);
			ret.setAccountRegisterTime(createRoleTime);
		}else{
			ret.setAccountRegisterTime(serverTime);
		}

		session.write(ret.build());

		EventMgr.addEvent(junZhuId,ED.ACC_LOGIN, junZhuId);
		final IoSession previous = sessionMap.put(junZhuId, session);
		if (previous != null) {
			previous.write(PD.S_ACC_login_kick);
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
					}
					previous.close(false);
					log.error("{} 重复登录，踢掉前一个 {}", junZhuId, previous);
					IOHandlerImpl.inst.playerExitScene(previous);

				}
			}).start();
		}
	}

	public String getIp(IoSession session) {
		String ip = "";
		try{
			ip = ((InetSocketAddress)
					session.getRemoteAddress())
					.getAddress()
					.getHostAddress();
		}catch(Exception e){
			log.error("获取IP出错",e);
		}
		return ip;
	}

	public static IoSession getIoSession(Long key) {
		IoSession session = null;
		if (key != null) {
			session = sessionMap.get(key);
		}
		return session;
	}

	public void createRole0(final int id, final IoSession session,
			final Builder builder) {
				createRole(id, session, builder);
	}

	public void createRole(int id, IoSession session, Builder builder) {
		CreateRoleRequest.Builder request = (qxmobile.protobuf.ZhangHao.CreateRoleRequest.Builder) builder;
		int roleId = request.getRoleId();
		String roleName = request.getRoleName().trim();
		int guoJiaId = request.getGuoJiaId();
		
		CreateRoleResponse.Builder response = CreateRoleResponse.newBuilder();
		ZhuceRenwu zhuceRenwu = roleInfoList.get(roleId);
		if (zhuceRenwu == null) {
			response.setIsSucceed(false);
			response.setMsg("人物角色选择错误");
			session.write(response.build());
			return;
		}
		if (roleName.length() > NAME_LENGTH_JUNZHU_MAX) {
			response.setIsSucceed(false);
			response.setMsg("输入的名称过长！");
			session.write(response.build());
			return;
		}
		if (hasSensitiveWord(roleName)) {
			response.setIsSucceed(false);
			response.setMsg("输入的名称包含敏感词！");
			session.write(response.build());
			return;
		}
		if(roleName.contains("卍") || roleName.contains("卐")){
			response.setIsSucceed(false);
			response.setMsg("输入文字中有非法字符！");
			session.write(response.build());
			return;
		}
		if (hasSpecial(roleName)) {
			response.setIsSucceed(false);
			response.setMsg("仅限使用中/英文以及数字！");
			session.write(response.build());
			return;
		}
		if (guoJiaId < guoJiaFirstId || guoJiaId > guoJiaLastId) {
			response.setIsSucceed(false);
			response.setMsg("所选国家不再1~7范围内");
			session.write(response.build());
			return;
		}
		createRoleSync(roleName, response, session, roleId, guoJiaId);
	}
	public void createRoleSync(String roleName,CreateRoleResponse.Builder response,IoSession session, int roleId, int guoJiaId){
//		log.info("--------1---{}", System.currentTimeMillis() - time);
		long time = System.currentTimeMillis();
		// 名字是否已存在
		// mysql 记录不区分大小写，所以，若用memcached（区分）查询会出问题 20150826
//		JunZhu junZhu = HibernateUtil.find(JunZhu.class,  " where name='" + roleName +"'", false);
//		 JunZhu junZhu = HibernateUtil.findByName(JunZhu.class, roleName,
//		 " where name='" + roleName +"'");
		String nameKey = "NameCheck:"+roleName.toLowerCase();
		boolean ok = MemcachedCRUD.getMemCachedClient().add(nameKey, 1);
//		boolean exists = MemcachedCRUD.getInstance().getMemCachedClient()
//				.keyExists("JunZhu:" + roleName);
		if (ok == false) {
			response.setIsSucceed(false);
			response.setMsg("该名称已被其他玩家使用！");
			session.write(response.build());
			return;
		}
//		log.info("------2-----{}", System.currentTimeMillis() - time);
		time = System.currentTimeMillis();
		 /*//随机角色名字要交给客户端来做了。
		 NameKu nameKu = HibernateUtil.find(NameKu.class,
		 " where sex="+zhuceRenwu.getSex()+" and name='"+roleName+"'");
		 if(nameKu != null) {
		 nameKu.setIsUse((byte)1);
		 HibernateUtil.save(nameKu);
		 ZhuceRenwu role = roleInfoList.get(roleId);
		 roleNameMap.get(role.getSex()).remove(nameKu);
		 }
		 log.info("-----3------{}", System.currentTimeMillis() - time);
		 time = System.currentTimeMillis();*/
		
		// 创建君主
		long junZhuId = (Long) session.getAttribute(SessionAttKey.junZhuId);
		JunZhu newJunZhu = JunZhuMgr.inst.fixCreateJunZhu(junZhuId, roleName, roleId, guoJiaId);
		if (newJunZhu == null) {
			return;
		}
		PveRecordDao.cache.put(junZhuId,Collections.synchronizedMap(new LinkedHashMap<Integer, PveRecord>()));
		MiBaoDao.cache.put(junZhuId,Collections.synchronizedMap(new LinkedHashMap<Integer, MiBaoDB>()));
		GameTaskMgr.GameTaskCache.put(junZhuId, new LinkedList<>());
		BigSwitch.inst.jiNengPeiYangMgr.addCache(junZhuId);
		YaBiaoHuoDongMgr.inst.initYaBiaoBeanInfo(junZhuId,newJunZhu.vipLevel);
		YaBiaoHuoDongMgr.inst.initYBBattleInfo(junZhuId, newJunZhu.vipLevel);
		ChengHaoDao.cache.put(junZhuId,Collections.synchronizedMap(new LinkedHashMap<Integer, ChengHaoBean>()));
		TalentAttr attr = new TalentAttr(junZhuId);
		HibernateUtil.insert(attr);
		Cache.talentAttrCache.put(junZhuId, attr);
		Cache.allianceApplyCache.put(junZhuId, AllianceMgr.defaultAllianceApply);
		PurchaseMgr.inst.getDefaultTili(junZhuId);
		MibaoMgr.inst.getDefaultMibaoLevelPoint(newJunZhu);
		GuoJiaMgr.inst.initResourceGongJin(junZhuId);
		int channel = OurLog.chCode.getChCodeByRoleId(junZhuId);
		DelayedSQLMgr.inst.cunLiangAdd(junZhuId, channel, roleName, roleId);
		log.info("------fixCreateJunZhu-----{}", System.currentTimeMillis() - time);
		session.setAttribute(SessionAttKey.JUN_XIAN_ID, PVPConstant.XIAO_ZU_JI_BIE);
		time = System.currentTimeMillis();
		response.setIsSucceed(true);
		response.setMsg("角色创建成功");
		session.write(response.build());
		MemcachedCRUD.getMemCachedClient().add("JunZhu:" + roleName, junZhuId);

		// 2015年7月3日 18:27 初始化GM中的角色状态为正常
		Redis.getInstance().set(GMRoleMgr.CACHE_ROLE_BAN_USER + junZhuId, "0");
		Redis.getInstance().set(GMRoleMgr.CACHE_ROLE_BAN_USER_SPEAK + junZhuId, "0");

		// 统计国家的人数
		GuoJiaMgr.setGuoJiaPlayerNumber(guoJiaId);
		log.info("------5-----{}", System.currentTimeMillis() - time);
		time = System.currentTimeMillis();
		//创建角色不选国家了。
//		Integer guoJiaTuijian = (Integer) session.getAttribute(SessionAttKey.NEW_PLAYER_GUOJIA_TUIJIAN);
//		if(guoJiaTuijian != null && guoJiaTuijian == guoJiaId) {
//			session.removeAttribute(SessionAttKey.NEW_PLAYER_GUOJIA_TUIJIAN);
//			Jiangli guojiaJiangli = PurchaseMgr.inst.jiangliMap.get(1);
//			if(guojiaJiangli == null) {
//				log.error("找不到推荐国家奖励：jiangliId:{}", 1);
//			} else {
//				List<AwardTemp> awardList = AwardMgr.inst.parseAwardConf(guojiaJiangli.item, "#", ":");
//				log.info("选择推荐国家奖励:{},发放给玩家:{}", awardList, junZhuId);
//				for(AwardTemp at : awardList) {
//					AwardMgr.inst.giveReward(session, at, newJunZhu);
//				}
//			}
//		}

		// 向登录服务器发送成功创建的角色相关情况
		EventMgr.addEvent(junZhuId, ED.CREATE_JUNZHU_SUCCESS, newJunZhu);
		// 君主登陆
		//2016年7月29日10:45:46，创建君主时不触发这个了。只有再次登录才触发。新建的角色应该没有什么事儿要做。
//		EventMgr.addEvent(junZhuId, ED.JUNZHU_LOGIN, newJunZhu);
		CreateJunZhuSer s = new CreateJunZhuSer(newJunZhu.id, newJunZhu.name);
		s.start();
	}

	public boolean hasSensitiveWord(String roleName) {
		for (String s : sensitiveWord) {
			if (roleName != null && roleName.contains(s)) {
				return true;
			}
		}
		return false;
	}
	
	// 名字只能含有汉字英文字母数字
	public boolean hasSpecial(String roleName) {
		String regx = "^[a-zA-Z0-9\u4e00-\u9fa5]+$";
		return !Pattern.matches(regx, roleName);
	}

	public void roleNameRequest0(final int id, final IoSession session,
			final Builder builder) {
				roleNameRequest(id, session, builder);
	}

	public void roleNameRequest(int id, IoSession session, Builder builder) {
		RoleNameRequest.Builder request = (qxmobile.protobuf.ZhangHao.RoleNameRequest.Builder) builder;
		int roleId = request.getRoleId();
		ZhuceRenwu role = roleInfoList.get(roleId);
		if (role == null) {
			log.error("选择的玩家角色不存在");
			return;
		}
		List<NameKu> list = roleNameMap.get(role.sex);
		if (list.size() == 0) {
			log.error("nameKu配置信息不正确");
			return;
		}
		int index = RandomUtil.getRandomNum(list.size());
		NameKu nameKu = list.get(index);
		String roleName = nameKu.name;
		RoleNameResponse.Builder response = RoleNameResponse.newBuilder();
		response.setRoleName(roleName);
		session.write(response.build());
	}

	public void insertRoleNameToDatabase() {
		SAXReader reader = new SAXReader();
		Document document = null;
		try {
			document = reader.read(new File("conf/classes/data/NameKu.xml"));
			Element rootEle = document.getRootElement();
			Iterator<Element> iterator = rootEle.elementIterator("NameKu");
			while (iterator.hasNext()) {
				Element nameEle = iterator.next();
				int id = Integer.parseInt(nameEle.attributeValue("id"));
				String name = nameEle.attributeValue("nameId");
				int sex = Integer.parseInt(nameEle.attributeValue("sex"));
				NameKu nameKu = new NameKu(id, name, sex, (byte) 0);
				HibernateUtil.insert(nameKu);
			}
		} catch (DocumentException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 已进入选人界面，需要记录，以免再次去打第0关。
	 * @param id
	 * @param session
	 * @param builder
	 */
	public void enterCreateRole(int id, IoSession session, Builder builder) {
		Long junZhuId = (Long) session.getAttribute(SessionAttKey.junZhuId);
		if(junZhuId==null){
			log.error("null jz id {}",session);
			return;
		}
		String jzId = String.valueOf(junZhuId);
		/*PrepareJz bean = HibernateUtil.find(PrepareJz.class, junZhuId);
		if(bean != null){
			return;
		}*/
		if(Redis.instance.sexist("PrepareJz", jzId)){
			return;
		}
//		bean = new PrepareJz();
//		bean.dt = new Date();
//		bean.jzId = junZhuId;
//		HibernateUtil.insert(bean);
		Redis.instance.sadd("PrepareJz", jzId);
		log.info("{} enter create role",junZhuId);
	}
}
