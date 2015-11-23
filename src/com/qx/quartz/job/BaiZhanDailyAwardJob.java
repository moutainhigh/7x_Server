package com.qx.quartz.job;

import java.util.List;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.manu.network.SessionManager;
import com.qx.junzhu.JunZhu;
import com.qx.persistent.HibernateUtil;
import com.qx.pvp.PVPConstant;
import com.qx.pvp.PvpMgr;

public class BaiZhanDailyAwardJob implements Job {
	private Logger logger = LoggerFactory.getLogger(BaiZhanDailyAwardJob.class);
	@Override
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		logger.info("BaiZhanDailyAwardJob 开始");

		List<Long> list = SessionManager.inst.getAllOnlineJunZhuId();
		JunZhu jz = null;
		if(list == null){
			return;
		}
		for (Long id: list){
			if(id == null){
				continue;
			}
			long junid = Long.valueOf(id);
			jz = HibernateUtil.find(JunZhu.class, junid);
			if(jz != null){
				PvpMgr.inst.addDailyAward(junid, PVPConstant.ONLINE_SEND_EMAIL);
			}
		}
		logger.info("BaiZhanDailyAwardJob 结束");
	}
}
   