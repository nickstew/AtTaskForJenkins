package com.attask.jenkins.plugin;

import org.apache.commons.io.output.NullOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

public class DependencyFactory {

	private static Map<String, RemoteConnector> connectorCache = new HashMap<String, RemoteConnector>();

	public static RemoteConnector getConnector(String url, String adminUsername, String adminPassword) throws Exception {
		return getConnector(url, adminUsername, adminPassword, new PrintStream(new NullOutputStream()));
	}

	public static RemoteConnector getConnector(String url, String adminUsername, String adminPassword, PrintStream logger) throws Exception {
		return getConnector(url, adminUsername, adminUsername, adminPassword, logger);
	}

	public static RemoteConnector getConnector(String url, String loginAsUsername, String adminUsername, String adminPassword, PrintStream logger) throws Exception {
		if (!connectorCache.containsKey(loginAsUsername)) {
			connectorCache.put(loginAsUsername, new AtTaskConnector(adminUsername, adminPassword, url, logger));
			if (loginAsUsername != null && !loginAsUsername.equals(adminUsername))
				connectorCache.get(loginAsUsername).loginAsUser(loginAsUsername);
		}

		return connectorCache.get(loginAsUsername);
	}
}
