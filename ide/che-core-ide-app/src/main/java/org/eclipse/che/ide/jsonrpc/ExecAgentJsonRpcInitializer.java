/*******************************************************************************
 * Copyright (c) 2012-2017 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.jsonrpc;

import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.machine.events.ExecAgentServerRunningEvent;
import org.eclipse.che.ide.api.machine.events.ExecAgentServerStoppedEvent;
import org.eclipse.che.ide.api.workspace.model.MachineImpl;
import org.eclipse.che.ide.api.workspace.model.RuntimeImpl;
import org.eclipse.che.ide.api.workspace.model.ServerImpl;
import org.eclipse.che.ide.api.workspace.model.WorkspaceImpl;
import org.eclipse.che.ide.bootstrap.BasicIDEInitializedEvent;
import org.eclipse.che.ide.util.loging.Log;

import javax.inject.Singleton;
import java.util.Optional;

import static java.util.Collections.singletonMap;
import static org.eclipse.che.api.core.model.workspace.WorkspaceStatus.RUNNING;
import static org.eclipse.che.api.machine.shared.Constants.EXEC_AGENT_REFERENCE;

/** Initializes JSON-RPC connection to the ws-agent server. */
@Singleton
public class ExecAgentJsonRpcInitializer {

    private final AppContext         appContext;
    private final JsonRpcInitializer initializer;

    @Inject
    public ExecAgentJsonRpcInitializer(JsonRpcInitializer initializer, AppContext appContext, EventBus eventBus) {
        this.appContext = appContext;
        this.initializer = initializer;

        eventBus.addHandler(ExecAgentServerRunningEvent.TYPE, event -> initializeJsonRpcService(event.getMachineName()));
        eventBus.addHandler(ExecAgentServerStoppedEvent.TYPE, event -> initializer.terminate(event.getMachineName()));

        // in case workspace is already running
        eventBus.addHandler(BasicIDEInitializedEvent.TYPE, event -> {
            final WorkspaceImpl workspace = appContext.getWorkspace();

            if (workspace.getStatus() == RUNNING) {
                final RuntimeImpl runtime = workspace.getRuntime();

                if (runtime != null) {
                    runtime.getMachines()
                           .values()
                           .stream()
                           .map(MachineImpl::getName)
                           .forEach(this::initializeJsonRpcService);
                }
            }
        });
    }

    private void initializeJsonRpcService(String machineName) {
        Log.debug(ExecAgentJsonRpcInitializer.class, "Web socket agent started event caught.");

        try {
            internalInitialize(machineName);
        } catch (Exception e) {
            Log.debug(ExecAgentJsonRpcInitializer.class, "Failed, will try one more time.");

            new Timer() {
                @Override
                public void run() {
                    internalInitialize(machineName);
                }
            }.schedule(1_000);
        }
    }

    private void internalInitialize(String machineName) {
        final WorkspaceImpl workspace = appContext.getWorkspace();
        final RuntimeImpl runtime = workspace.getRuntime();

        if (runtime == null) {
            return; // workspace is stopped
        }

        runtime.getMachineByName(machineName).ifPresent(machine -> {
            Optional<ServerImpl> execAgentServer = machine.getServerByName(EXEC_AGENT_REFERENCE);

            execAgentServer.ifPresent(server -> {
                String execAgentServerURL = server.getUrl();
                // TODO (spi ide): remove path when it comes with URL
                execAgentServerURL = execAgentServerURL.replaceFirst("http", "ws") + "/connect";

                initializer.initialize(machine.getName(), singletonMap("url", execAgentServerURL));
            });
        });
    }
}