package org.giiwa.activemq.web;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.giiwa.activemq.mq.MQ;
import org.giiwa.activemq.web.admin.activemq;
import org.giiwa.core.bean.X;
import org.giiwa.core.conf.Global;
import org.giiwa.core.task.Task;
import org.giiwa.framework.bean.OpLog;
import org.giiwa.framework.web.IListener;
import org.giiwa.framework.web.Module;

public class ActivemqListener implements IListener {

  static Log log = LogFactory.getLog(ActivemqListener.class);

  @Override
  public void onStart(Configuration conf, Module m) {
    // TODO Auto-generated method stub
    log.info("activemq is starting ...");

    if (X.isSame("on", Global.getString("activemq.enabled", X.EMPTY))) {
      new Task() {

        @Override
        public void onExecute() {
          MQ.init(conf);
        }

      }.schedule(10);
    } else {
      OpLog.info(activemq.class, "startup", "disabled", null, null);
    }
  }

  @Override
  public void onStop() {
    // TODO Auto-generated method stub

  }

  @Override
  public void uninstall(Configuration conf, Module m) {
    // TODO Auto-generated method stub

  }

  @Override
  public void upgrade(Configuration conf, Module m) {
    // TODO Auto-generated method stub

  }

}
