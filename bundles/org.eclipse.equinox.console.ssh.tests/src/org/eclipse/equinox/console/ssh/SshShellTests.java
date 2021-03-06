/*******************************************************************************
 * Copyright (c) 2011, 2020 SAP AG and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Lazar Kirchev, SAP AG - initial API and implementation
 *******************************************************************************/

package org.eclipse.equinox.console.ssh;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.felix.service.command.CommandProcessor;
import org.apache.felix.service.command.CommandSession;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.channel.ChannelSession;
import org.easymock.EasyMock;
import org.eclipse.equinox.console.common.ConsoleInputStream;
import org.eclipse.equinox.console.storage.DigestUtil;
import org.eclipse.equinox.console.storage.SecureUserStore;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.BundleContext;

public class SshShellTests {

	private static final int TEST_CONTENT = 100;
	private static final String USER_STORE_FILE_NAME = "org.eclipse.equinox.console.jaas.file";
	private static final String DEFAULT_USER_STORAGE = "osgi.console.ssh.useDefaultSecureStorage";
	private static final String USER_STORE_NAME = SshShellTests.class.getName() + "_store";
	private static final String HOST = "localhost";
	private static final String GOGO_SHELL_COMMAND = "gosh --login --noshutdown";
	private static final String TERM_PROPERTY = "TERM";
	private static final String XTERM = "XTERM";
	private static final String USERNAME = "username";
	private static final String PASSWORD = "password";
	private static final String TRUE = "true";

	@Before
	public void init() throws Exception {
		clean();
		initStore();
	}

	@Test
	public void testSshConnection() throws Exception {
		SshShell shell = null;

		try (ServerSocket servSocket = new ServerSocket(0);
				Socket socketClient = new Socket(HOST, servSocket.getLocalPort());
				Socket socketServer = servSocket.accept()) {
			try (CommandSession session = EasyMock.createMock(CommandSession.class)) {
				EasyMock.makeThreadSafe(session, true);
				EasyMock.expect(session.put((String) EasyMock.anyObject(), EasyMock.anyObject()))
						.andReturn(EasyMock.anyObject());
				EasyMock.expect(session.execute(GOGO_SHELL_COMMAND)).andReturn(null);
				session.close();
				EasyMock.expectLastCall();
				EasyMock.replay(session);
				CommandProcessor processor = EasyMock.createMock(CommandProcessor.class);
				EasyMock.expect(processor.createSession((ConsoleInputStream) EasyMock.anyObject(),
						(PrintStream) EasyMock.anyObject(), (PrintStream) EasyMock.anyObject())).andReturn(session);
				EasyMock.replay(processor);

				BundleContext context = EasyMock.createMock(BundleContext.class);
				EasyMock.makeThreadSafe(context, true);
				EasyMock.expect(context.getProperty(DEFAULT_USER_STORAGE)).andReturn(TRUE);
				EasyMock.replay(context);

				Map<String, String> environment = new HashMap<>();
				environment.put(TERM_PROPERTY, XTERM);
				Environment env = EasyMock.createMock(Environment.class);
				EasyMock.expect(env.getEnv()).andReturn(environment);
				EasyMock.replay(env);

				List<CommandProcessor> processors = new ArrayList<>();
				processors.add(processor);
				shell = new SshShell(processors, context);
				shell.setInputStream(socketServer.getInputStream());
				shell.setOutputStream(socketServer.getOutputStream());
				shell.start(new ChannelSession(), env);
			}

			try (OutputStream outClient = socketClient.getOutputStream()) {
				outClient.write(TEST_CONTENT);
				outClient.write('\n');
				outClient.flush();
				try (InputStream input = socketClient.getInputStream()) {
					int in = input.read();
					Assert.assertTrue(
							"Server received [" + in + "] instead of " + TEST_CONTENT + " from the ssh client.",
							in == TEST_CONTENT);
				}
			}
		}
	}

	@After
	public void cleanUp() {
		clean();
	}

	private void initStore() throws Exception {
		System.setProperty(USER_STORE_FILE_NAME, USER_STORE_NAME);
		SecureUserStore.initStorage();
		SecureUserStore.putUser(USERNAME, DigestUtil.encrypt(PASSWORD), null);
	}

	private void clean() {
		System.setProperty(USER_STORE_FILE_NAME, "");
		File file = new File(USER_STORE_NAME);
		if (file.exists()) {
			file.delete();
		}
	}
}
