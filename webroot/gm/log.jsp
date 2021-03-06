<%@page import="java.util.HashMap"%>
<%@page import="java.util.Map"%>
<%@page import="java.lang.reflect.Field"%>
<%@page import="com.manu.network.PD"%>
<%@page import="com.qx.log.LogMgr"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Insert title here</title>
</head>
<body>
	<%
	String receiveState = request.getParameter("receiveState");
	String sendState = request.getParameter("sendState");
	if(receiveState==null){
		receiveState = String.valueOf(LogMgr.receiveConsole);
	}
	
	if(sendState==null){
		sendState = String.valueOf(LogMgr.sendConsole);
	}
	String receiveLog = request.getParameter("receiveLog");
	String sendLog = request.getParameter("sendLog");
	if(receiveLog==null){
		receiveLog = String.valueOf(LogMgr.receiveLog);
	}
	if(sendLog==null){
		sendLog = String.valueOf(LogMgr.sendLog);
	}
	%>
	<table border="1">
	<tr>
		<td>
			<a href="receiveProtoIdLog.jsp">客户端-->服务器协议日志记录</a>
		</td>
		<td>
			后台输出：
			<%
			LogMgr.receiveConsole = Boolean.valueOf(receiveState);
			if(Boolean.valueOf(LogMgr.receiveConsole)){
				%>
				<a>开启</a>|<a href="log.jsp?receiveState=false">关闭</a>
				<%
			} else{
				%>
				<a href="log.jsp?receiveState=true">开启</a>|<a>关闭</a>
				<%
			}
			%>
		</td>
		<td>
			日志记录：
			<%
			LogMgr.receiveLog = Boolean.valueOf(receiveLog);
			if(Boolean.valueOf(LogMgr.receiveLog)){
				%>
				<a>开启</a>|<a href="log.jsp?receiveLog=false">关闭</a>
				<%
			} else{
				%>
				<a href="log.jsp?receiveLog=true">开启</a>|<a>关闭</a>
				<%
			}
			%>
		</td>
	</tr>
	<tr>
		<td>
			<a href="sendProtoIdLog.jsp">服务器-->客户端协议日志记录</a>
		</td>
		<td>
			后台输出：
			<%
			LogMgr.sendConsole = Boolean.valueOf(sendState);
			if(Boolean.valueOf(LogMgr.sendConsole)){
				%>
				<a>开启</a>|<a href="log.jsp?sendState=false">关闭</a>
				<%
			} else{
				%>
				<a href="log.jsp?sendState=true">开启</a>|<a>关闭</a>
				<%
			}
			%>
		</td>
		<td>
			日志记录：
			<%
			LogMgr.sendLog = Boolean.valueOf(sendLog);
			if(Boolean.valueOf(LogMgr.sendLog)){
				%>
				<a>开启</a>|<a href="log.jsp?sendLog=false">关闭</a>
				<%
			} else{
				%>
				<a href="log.jsp?sendLog=true">开启</a>|<a>关闭</a>
				<%
			}
			%>
		</td>
	</tr>
	</table>
	
	<a href="battlePackageInfo.jsp">查看战斗协议数据包信息</a> <br/><br/><br/>
	<%
		Map<Short, Short> cmdMap = new HashMap<>();
		Field[] fields = PD.class.getDeclaredFields();
		out.println("协议数量：" + fields.length + "<br/><br/>");
		
		int repeatCount = 0;
		for(Field f : fields) {
			if(f.getType() == short.class) {
				short cmd = f.getShort(f.getName());
				Short count = cmdMap.get(cmd);
				if(count == null) {
					cmdMap.put(cmd, (short)1);
				} else {
					cmdMap.put(cmd, (short)(count + 1));
					repeatCount++;
				}
			}
		}
		out.println("协议号重复数量：" + repeatCount + "<br/><br/>");
		for(Short key : cmdMap.keySet()) {
			short count = cmdMap.get(key);
			if(count > 1) {
				out.println("重复协议号：" + key+"<br/>");
			}
		}
	%>
</body>
</html>