<%@page import="org.apache.mina.core.session.IoSession"%>
<%@page import="com.manu.network.SessionManager"%>
<%@page import="com.manu.network.SessionUser"%>
<%@page import="com.qx.junzhu.JunZhu"%>
<%@page import="com.qx.award.AwardMgr"%>
<%@page import="com.manu.dynasty.template.AwardTemp"%>
<%@page import="com.manu.dynasty.template.MibaoSuiPian"%>
<%@page import="com.manu.dynasty.template.MibaoStar"%>
<%@page import="com.manu.dynasty.template.MiBao"%>
<%@page import="com.qx.mibao.MibaoMgr"%>
<%@page import="com.manu.dynasty.hero.service.HeroService"%>
<%@page import="java.util.List"%>
<%@page import="java.util.Map"%>
<%@page import="com.qx.mibao.MiBaoDB"%>
<%@page import="com.manu.dynasty.boot.GameServer"%>
<%@page import="com.qx.persistent.HibernateUtil"%>
<%@page import=" com.qx.util.TableIDCreator"%>
<%@page import="com.qx.account.Account"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>秘宝</title>
</head>
<body>
	<%
	String name = request.getParameter("account");
name = name == null ? "": name.trim();
String accIdStr = request.getParameter("accId");// 用户id
accIdStr = (accIdStr == null ? "":accIdStr.trim());
if(session.getAttribute("name") != null && name.length()==0 && accIdStr.length()==0){
	name = (String)session.getAttribute("name");
}
%>
	<form action="">
		账号<input type="text" name="account" value="<%=name%>">&nbsp;或&nbsp;
		账号ID<input type="text" name="accId" value="<%=accIdStr%>">
		<button type="submit">查询</button>
	</form>
	<br>
	<%
	Account account = null;
	if(name != null && name.length()>0){
		account = HibernateUtil.getAccount(name);
	}else if(accIdStr.length()>0){
		account = HibernateUtil.find(Account.class, Long.valueOf(accIdStr));
		if(account != null)name = account.getAccountName();
	}
	if(account == null){
		%>没有找到<%
	}else{
		session.setAttribute("name", name);
		%>账号<%=account.getAccountId()%>:<%=account.getAccountName()%>
		<br/>
		<table>
			<tr>
				<td>
					<form action="">
					秘宝id（MiBao.xml的id 也是下面 的【秘宝ID】）<input type="text" name="mibaoId" ><br/>
					<!-- 添加碎片数量<input type="text" name="num" ><br/> -->
					<input type='hidden' name='action' value="add"/>
					<button type="submit">添加一个完整秘宝</button>
					</form>
					<br/>
					<br/>
					<form action="">
					秘宝碎片id（MibaoSuiPian.xml的id ）<input type="text" name="mibaoSuiPianId" ><br/>
					添加碎片数量<input type="text" name="num" ><br/>
                    <input type='hidden' name='action' value="addSuipian"/>
                    <button type="submit">添加秘宝碎片</button>
					</form>
				</td>
			</tr>
		</table>
		<%
		//--------------------------------------
		long junZhuId = account.getAccountId() * 1000 + GameServer.serverId;
		JunZhu jz= HibernateUtil.find(JunZhu.class, junZhuId);
		if(jz != null){
			String action = request.getParameter("action");
			if("add".equals(action)){
				int mibaoId = Integer.parseInt(request.getParameter("mibaoId").trim());
				//int num = Integer.parseInt(request.getParameter("num"));
				AwardTemp a = new AwardTemp();
				a.setAwardId(0);
				a.setItemId(mibaoId);
				a.setItemNum(1);
				a.setItemType(AwardMgr.TYPE_MI_BAO);
				SessionUser su = SessionManager.inst.findByJunZhuId(junZhuId);
                if(su!=null){
                    AwardMgr.inst.giveReward(su.session, a, jz, false, false);
                }else{
                	AwardMgr.inst.giveReward(null, a, jz, false, false);
                }
                out.println("<br/>添加成功<br/>");
			}else if("addSuipian".equals(action)){
				int suipianId = Integer.parseInt(request.getParameter("mibaoSuiPianId"));
                int num = Integer.parseInt(request.getParameter("num"));
                AwardTemp a = new AwardTemp();
                a.setAwardId(0);
                a.setItemId(suipianId);
                a.setItemNum(num);
                a.setItemType(AwardMgr.TYPE_MOBAI_SUIPIAN);
                SessionUser su = SessionManager.inst.findByJunZhuId(junZhuId);
                if(su!=null){
                    AwardMgr.inst.giveReward(su.session, a, jz, false, false);
                }else{
                    AwardMgr.inst.giveReward(null, a, jz, false, false);
                }
                out.println("<br/>添加成功<br/>");
				
				
/* 		       certainAward.setAwardId(11111);
		      certainAward.setItemId(certainItem.getId());
		      certainAward.setItemType(ExploreConstant.QIANG_HUA_TYPE);
		      if (type == ExploreConstant.PAY || type == ExploreConstant.GUILD_2) {
	//	          certainAward.setItemNum(10);
				if(MibaoMgr.mibaoMap.containsKey(mibaoId)){
					MiBao cfg = MibaoMgr.mibaoMap.get(mibaoId);
					MiBaoDB miBaoDB = HibernateUtil.find(MiBaoDB.class, " where tempId=" + cfg.tempId +" and ownerId="+junZhuId);
					if(miBaoDB == null){
						long dbId = TableIDCreator.getTableID(MiBaoDB.class, 1L);
						miBaoDB = new MiBaoDB();
						miBaoDB.setDbId(dbId);
						miBaoDB.setTempId(cfg.tempId);
						miBaoDB.setSuiPianNum(num);
						miBaoDB.setOwnerId(junZhuId);
						miBaoDB.setMiBaoId(0);
						HibernateUtil.save(miBaoDB);
					}else{
						miBaoDB.setSuiPianNum(miBaoDB.getSuiPianNum()+num);
						HibernateUtil.save(miBaoDB);
					}
					out.println("<br/>添加成功<br/>");
				}else{
					out.println("<br/>没有这个秘宝 id - "+mibaoId+"<br/>");
				} */
			} else if("update".equals(action)) {
				int tempId = Integer.parseInt(request.getParameter("tempId"));
				int mibaoId = Integer.parseInt(request.getParameter("mibaoId"));
				MiBaoDB miBaoDB = HibernateUtil.find(MiBaoDB.class, " where tempId=" + tempId+" and ownerId="+junZhuId);
				miBaoDB.setMiBaoId(mibaoId);
				HibernateUtil.save(miBaoDB);
			} else if("delMibao".equals(action)) {
				long dbId = Long.parseLong(request.getParameter("dbId"));
				MiBaoDB miBaoDB = HibernateUtil.find(MiBaoDB.class, dbId);
				HibernateUtil.delete(miBaoDB);
			}
		}
		List<MiBaoDB> miBaoDBList = HibernateUtil.list(MiBaoDB.class, " where ownerId=" + junZhuId);
		 out.println("<br/>");
		out.println("当前秘宝(包括不完整秘宝)个数:"+miBaoDBList.size());
		%>
		<table border='1'>
		<tr><th>数据库id</th><th>秘宝ID</th><th>模板id</th><th>碎片id</th>
		<th>秘宝名称</th><th>数量</th><th>合成需要数量</th><th>升星需要数量</th>
		<th>等级</th><th>星星</th><th>操作</th></tr>
		<% 
		for(MiBaoDB bean : miBaoDBList){
			String mibaoName = "";
			if(bean.getMiBaoId()  > 0) {
				MiBao mibao = MibaoMgr.inst.mibaoMap.get(bean.getMiBaoId());
				mibaoName = HeroService.getNameById(mibao.nameId+"");
			}else{
				for(Map.Entry<Integer, MiBao> entry : MibaoMgr.inst.mibaoMap.entrySet()) {
					if(entry.getValue().tempId == bean.getTempId()) {
						MiBao mibao = entry.getValue();
						mibaoName = HeroService.getNameById(mibao.nameId+"");
						break;
					}
				}
			}

			MibaoSuiPian spConf = MibaoMgr.inst.mibaoSuipianMap.get(bean.getTempId());
			%>
			<tr>
			<td><%=bean.getDbId() %></td>
			<td><%=bean.getMiBaoId() %></td>
			<td><%=bean.getTempId() %></td>
			<td><%=spConf.getId() %></td>
			<td><%=mibaoName %></td>
			<td><%=bean.getSuiPianNum() %></td>
			<td><%
			out.append(""+(spConf == null ? 0 : spConf.getHechengNum()));
			%></td>
			<td><%
			MibaoStar starConf = MibaoMgr.inst.mibaoStarMap.get(bean.getStar());
			out.append(""+(starConf == null ? 0 : starConf.getNeedNum()));
			%></td>
			<td><%=bean.getLevel()%></td>
			<td><%=bean.getStar()%></td>
			<td><% out.append("<a href=mibao.jsp?dbId=" + bean.getDbId() + "&action=delMibao>删除</a>"); %></td>
			</tr>
			<%
		}
		%>
		</table>
		<%
	}
	%>
	<%//--------------------------------------------------------------------- %>
</body>
</html>