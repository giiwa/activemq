/*
 * Copyright 2015 JIHU, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package org.giiwa.activemq.web.admin;

import org.giiwa.core.bean.Beans;
import org.giiwa.core.bean.X;
import org.giiwa.core.bean.Helper.W;
import org.giiwa.core.conf.Global;
import org.giiwa.framework.bean.OpLog;
import org.giiwa.framework.web.Model;
import org.giiwa.framework.web.Path;

// TODO: Auto-generated Javadoc
public class activemq extends Model {

  /*
   * (non-Javadoc)
   * 
   * @see org.giiwa.framework.web.Model#onGet()
   */
  @Path(login = true, access = "access.config.admin")
  public void onGet() {
    int s = this.getInt("s");
    int n = this.getInt("n", 20, "number.per.page");

    W q = W.create("model", "admin.activemq").sort("created", -1);

    Beans<OpLog> bs = OpLog.load(q, s, n);
    this.set(bs, s, n);

    this.show("/admin/activemq.logs.html");
  }

  /**
   * Setting.
   */
  @Path(path = "setting", login = true, access = "access.config.admin")
  public void setting() {
    if (method.isPost()) {
      Global.setConfig("activemq.enabled", X.isSame("on", this.getString("enabled")) ? "on" : "off");
      Global.setConfig("activemq.url", this.getString("url"));
      Global.setConfig("activemq.group", this.getString("group"));
      Global.setConfig("activemq.user", this.getString("user"));
      Global.setConfig("activemq.passwd", this.getString("passwd"));

      this.set(X.MESSAGE, lang.get("restart.required"));
    }
    this.show("/admin/activemq.setting.html");
  }

}
