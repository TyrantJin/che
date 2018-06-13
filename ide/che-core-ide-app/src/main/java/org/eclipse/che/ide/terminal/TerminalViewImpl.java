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
package org.eclipse.che.ide.terminal;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focusable;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.Widget;
import javax.validation.constraints.NotNull;

/**
 * The class contains methods to display terminal.
 *
 * @author Dmitry Shnurenko
 */
final class TerminalViewImpl extends Composite implements TerminalView, Focusable, RequiresResize {

  interface TerminalViewImplUiBinder extends UiBinder<Widget, TerminalViewImpl> {}

  private static final TerminalViewImplUiBinder UI_BINDER =
      GWT.create(TerminalViewImplUiBinder.class);

  @UiField FlowPanel terminalPanel;

  @UiField Label unavailableLabel;

  private ActionDelegate delegate;

  private TerminalJso terminal;
  private boolean isOpen;
  private boolean focusOnOpen;

  public TerminalViewImpl() {
    initWidget(UI_BINDER.createAndBindUi(this));
  }

  @Override
  public void setDelegate(ActionDelegate delegate) {
    this.delegate = delegate;
  }

  /** {@inheritDoc} */
  @Override
  public void setTerminal(@NotNull final TerminalJso terminal, boolean focusOnOpen) {
    unavailableLabel.setVisible(false);

    terminalPanel.setVisible(true);
    this.focusOnOpen = focusOnOpen;
    this.terminal = terminal;
  }

  /** {@inheritDoc} */
  @Override
  public void showErrorMessage(@NotNull String message) {
    unavailableLabel.setText(message);
    unavailableLabel.setVisible(true);

    terminalPanel.setVisible(false);
  }

  /**
   * Resize {@link TerminalJso} to current widget size. To improve performance we should resize only
   * visible terminals, because "resize terminal" is quite expensive operation. When you click on
   * the tab to activate hidden terminal this method will be executed too, so terminal will be
   * resized anyway.
   */
  @Override
  public void onResize() {
    if (terminal != null && this.getElement().getClientWidth() > 0 && this.getElement().getClientHeight() > 0) {
        if (isOpen) {
            resizeTimer.schedule(200);
        } else {
            open();
        }
    }
  }

  private Timer resizeTimer =
      new Timer() {
        @Override
        public void run() {
          resizeTerminal();
        }
      };

  private void open() {
      terminal.open(terminalPanel.getElement());
      if (focusOnOpen) {
          terminal.focus();
      }
      isOpen = true;
  }

  private void resizeTerminal() {
    TerminalGeometryJso geometryJso = terminal.proposeGeometry();
    int x = geometryJso.getCols();
    int y = geometryJso.getRows();
    if (x <= 0 || y <= 0) {
      resizeTimer.cancel();
      resizeTimer.schedule(500);
      return;
    }

    delegate.setTerminalSize(x, y);
  }

  @Override
  public int getTabIndex() {
    return 0;
  }

  @Override
  public void setAccessKey(char key) {}

  private Timer focusTimer = //todo throw away focus timer
      new Timer() {
        @Override
        public void run() {
          terminal.focus();
        }
      };

  @Override
  public void setFocus(boolean focused) {
    if (terminal == null || terminal.getElement() == null) {
      return;
    }

    if (focused && !terminal.hasSelection()) {
      focusTimer.schedule(10);
    }
  }

  @Override
  public void setTabIndex(int index) {}
}
