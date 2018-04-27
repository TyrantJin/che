/*
 * Copyright (c) 2012-2018 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.core.db;

import static org.eclipse.che.api.system.shared.SystemStatus.READY_TO_SHUTDOWN;

import com.google.inject.Inject;
import com.google.inject.persist.PersistService;
import java.lang.reflect.Field;
import javax.inject.Singleton;
import javax.persistence.EntityManagerFactory;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.core.notification.EventSubscriber;
import org.eclipse.che.api.system.shared.dto.SystemStatusChangedEventDto;
import org.eclipse.persistence.internal.sessions.AbstractSession;
import org.eclipse.persistence.internal.sessions.coordination.jgroups.JGroupsRemoteConnection;
import org.eclipse.persistence.sessions.coordination.TransportManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stops {@link PersistService} when a system is ready to shutdown.
 *
 * @author Anton Korneta
 */
@Singleton
public class DBTermination {

  private static final Logger LOG = LoggerFactory.getLogger(DBTermination.class);

  @Inject
  public DBTermination(
      EventService eventService, PersistService persistService, EntityManagerFactory emFactory) {
    eventService.subscribe(
        new EventSubscriber<SystemStatusChangedEventDto>() {
          @Override
          public void onEvent(SystemStatusChangedEventDto event) {
            if (READY_TO_SHUTDOWN.equals(event.getStatus())) {
              try {
                LOG.info("Stopping persistence service.");
                fixJChannelClosing(emFactory);
                persistService.stop();
              } catch (RuntimeException ex) {
                LOG.error("Failed to stop persistent service. Cause: " + ex.getMessage());
              }
            }
          }
        },
        SystemStatusChangedEventDto.class);
  }

  /**
   * This method is hack that changes value of {@link JGroupsRemoteConnection#isLocal} to false.
   * This is needed to close the JGroups EclipseLinkCommandChannel and as result gracefully stop of
   * the system.<br>
   * For more details see {@link JGroupsRemoteConnection#closeInternal()}
   *
   * <p>TODO eclipse-link extension issue https://bugs.eclipse.org/bugs/show_bug.cgi?id=534148
   */
  private void fixJChannelClosing(EntityManagerFactory emFactory) {
    try {
      final AbstractSession session = emFactory.unwrap(AbstractSession.class);
      final TransportManager transportManager = session.getCommandManager().getTransportManager();
      final JGroupsRemoteConnection conn =
          (JGroupsRemoteConnection) transportManager.getConnectionToLocalHost();
      final Field isLocal = conn.getClass().getDeclaredField("isLocal");
      isLocal.setAccessible(true);
      isLocal.set(conn, false);
    } catch (IllegalAccessException | NoSuchFieldException ex) {
      LOG.error(
          "Failed to change JGroupsRemoteConnection#isLocal this may prevent the graceful stop of "
              + "the system because EclipseLinkCommandChannel won't be closed. Cause: "
              + ex.getMessage());
    }
  }
}
