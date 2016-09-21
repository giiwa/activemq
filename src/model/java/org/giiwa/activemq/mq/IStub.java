package org.giiwa.activemq.mq;

import org.giiwa.core.json.JSON;

/**
 * the message stub
 * 
 * @author joe
 *
 */
public interface IStub {

  public void onRequest(long seq, String to, String from, String src, JSON header, JSON msg, byte[] attachment);

  public void onResponse(long seq, String to, String from, String src, JSON header, JSON msg, byte[] attachment);

}
