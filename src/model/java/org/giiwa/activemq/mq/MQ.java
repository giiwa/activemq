package org.giiwa.activemq.mq;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.giiwa.activemq.web.admin.activemq;
import org.giiwa.core.bean.TimeStamp;
import org.giiwa.core.bean.X;
import org.giiwa.core.json.JSON;
import org.giiwa.core.task.Task;
import org.giiwa.framework.bean.OpLog;

/**
 * the distribute message system, <br>
 * the performance: sending 1w/300ms <br>
 * recving 1w/1500ms<br>
 * 
 * @author joe
 *
 */
public final class MQ {

  private static String group = X.EMPTY;

  private static Log    log   = LogFactory.getLog(MQ.class);

  /**
   * the message stub type <br>
   * TOPIC: all stub will read it <br>
   * QUEUE: only one will read it
   * 
   * @author joe
   *
   */
  public static enum Mode {
    TOPIC, QUEUE
  };

  private static final int                 REQUEST  = 1;
  private static final int                 RESPONSE = 2;

  private static Connection                connection;
  private static Session                   session;
  private static boolean                   enabled  = false;
  private static String                    url;             // failover:(tcp://localhost:61616,tcp://remotehost:61616)?initialReconnectDelay=100
  private static String                    user;
  private static String                    password;
  private static ActiveMQConnectionFactory factory;

  private static boolean init() {
    if (enabled && (session == null)) {
      try {
        if (factory == null) {
          factory = new ActiveMQConnectionFactory(user, password, url);
        }

        if (connection == null) {
          connection = factory.createConnection();
          connection.start();
        }

        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        OpLog.info(activemq.class, "startup", "connected ActiveMQ with [" + url + "]", null, null);

      } catch (Exception e) {
        log.error(e.getMessage(), e);

        OpLog.info(activemq.class, "startup", "failed ActiveMQ with [" + url + "]", null, null);
      }
    }

    return enabled && session != null;
  }

  private MQ() {
  }

  /**
   * initialize the MQ
   * 
   * @param conf
   * @return boolean
   */
  public static boolean init(Configuration conf) {
    if (session != null)
      return true;

    enabled = true;

    url = conf.getString("activemq.url", ActiveMQConnection.DEFAULT_BROKER_URL);
    user = conf.getString("activemq.user", ActiveMQConnection.DEFAULT_USER);
    password = conf.getString("activemq.passwd", ActiveMQConnection.DEFAULT_PASSWORD);

    group = conf.getString("activemq.group", X.EMPTY);
    if (!group.endsWith(".")) {
      group += ".";
    }

    return init();

  }

  /**
   * listen on the name
   * 
   * @param name
   * @param stub
   * @throws JMSException
   */
  public static Receiver bind(String name, IStub stub, Mode mode) throws JMSException {
    OpLog.info(activemq.class, "bind", "[" + name + "], stub=" + stub.getClass().toString() + ", mode=" + mode, null,
        null);

    return new Receiver(name, stub, mode);
  }

  public static Receiver bind(String name, IStub stub) throws JMSException {
    return bind(name, stub, Mode.QUEUE);
  }

  /**
   * QueueTask
   * 
   * @author joe
   * 
   */
  public final static class Receiver implements MessageListener {
    String          name;
    IStub           cb;
    MessageConsumer consumer;
    TimeStamp       t     = TimeStamp.create();
    int             count = 0;

    private Receiver(String name, IStub cb, Mode mode) throws JMSException {
      this.name = group + name;
      this.cb = cb;

      if (enabled) {
        Destination dest = null;
        if (mode == Mode.QUEUE) {
          dest = new ActiveMQQueue(group + name);
        } else {
          dest = new ActiveMQTopic(group + name);
        }

        consumer = session.createConsumer(dest);
        consumer.setMessageListener(this);
      } else {
        log.warn("no mq configured!");
      }
    }

    /**
     * 
     */
    public void unbind() {
      /**
       * TODO
       */

    }

    @Override
    public void onMessage(Message m) {
      try {
        // System.out.println("got a message.., " + t.reset() +
        // "ms");

        count++;
        if (m instanceof BytesMessage) {
          BytesMessage m1 = (BytesMessage) m;
          process(name, m1, cb);
        } else {
          System.out.println(m);
        }

        if (count % 10000 == 0) {
          System.out.println("process the 10000 messages, cost " + t.reset() + "ms");
        }

      } catch (Exception e) {
        log.error(e.getMessage(), e);
      }
    }
  }

  private static void process(final String name, final BytesMessage req, final IStub cb) {

    new Task() {
      @Override
      public void onExecute() {

        try {
          int command = req.readInt();
          long seq = req.readLong();
          String to = req.readUTF();
          String from = req.readUTF();
          String src = req.readUTF();

          long time = req.readLong();
          long delay = System.currentTimeMillis() - time;
          if (delay > 1000) {
            log.warn("MQ[" + name + "] reader delayed " + delay + "ms");
          }

          JSON header = null;
          int len = req.readInt();
          if (len > 0) {
            byte[] bb = new byte[len];
            req.readBytes(bb);
            ByteArrayInputStream is = new ByteArrayInputStream(bb);
            ObjectInputStream in = new ObjectInputStream(is);
            header = (JSON) in.readObject();
            in.close();
          }

          JSON message = null;
          len = req.readInt();
          if (len > 0) {
            byte[] bb = new byte[len];
            req.readBytes(bb);
            ByteArrayInputStream is = new ByteArrayInputStream(bb);
            ObjectInputStream in = new ObjectInputStream(is);
            message = (JSON) in.readObject();
            in.close();
          }

          byte[] bb = null;
          len = req.readInt();
          if (len > 0) {
            bb = new byte[len];
            req.readBytes(bb);
          }

          log.debug("got a message:" + src + ", " + message);

          if (command == MQ.REQUEST) {
            cb.onRequest(seq, to, from, src, header, message, bb);
          } else {

            if (handlers.containsKey(seq)) {
              /**
               * there is someone wait the response
               */
              IStub s = handlers.remove(seq);
              s.onResponse(seq, to, from, src, header, message, bb);
              synchronized (s) {
                s.notifyAll();
              }
            } else {
              cb.onResponse(seq, to, from, src, header, message, bb);
            }
          }
        } catch (Exception e) {
          log.error(e.getMessage(), e);
        }
      }
    }.schedule(0);

  }

  /**
   * broadcast the message as "topic" to all "dest:to", and return immediately
   * 
   * @param seq
   * @param dest
   * @param to
   * @param message
   * @param bb
   * @param src
   * @param from
   * @param header
   * @return 1: success<br>
   */
  public static int broadcast(long seq, String dest, String to, JSON message, byte[] bb, String src, String from,
      JSON header) {
    if (message == null)
      return -1;

    if (!enabled) {
      return -1;
    }

    try {

      /**
       * get the message producer by destination name
       */
      MessageProducer p = getTopic(dest);
      if (p != null) {
        BytesMessage resp = session.createBytesMessage();

        resp.writeInt(MQ.REQUEST);
        resp.writeLong(seq);
        resp.writeUTF(to);
        resp.writeUTF(from);
        resp.writeUTF(src);
        resp.writeLong(System.currentTimeMillis());

        if (header == null) {
          resp.writeInt(0);
        } else {
          ByteArrayOutputStream os = new ByteArrayOutputStream();
          ObjectOutputStream out = new ObjectOutputStream(os);
          out.writeObject(header);
          out.close();
          byte[] ss = os.toByteArray();
          resp.writeInt(ss.length);
          resp.writeBytes(os.toByteArray());
        }

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(os);
        out.writeObject(message);
        out.close();
        byte[] ss = os.toByteArray();
        resp.writeInt(ss.length);
        resp.writeBytes(os.toByteArray());

        if (bb == null) {
          resp.writeInt(0);
        } else {
          resp.writeInt(bb.length);
          resp.writeBytes(bb);
        }

        p.send(resp, DeliveryMode.NON_PERSISTENT, 0, X.AMINUTE);

        log.debug("AMQ:" + dest + ", " + message);

        return 1;
      }
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }

    return -1;
  }

  /**
   * send the message and waiting the response until timeout
   * 
   * @param dest
   * @param to
   * @param message
   * @param bb
   * @param src
   * @param from
   * @param header
   * @param handler
   * @param timeout
   * @return 1: success<br>
   */
  public static int call(String dest, String to, JSON message, byte[] bb, String src, String from, JSON header,
      IStub handler, long timeout) {
    if (message == null)
      return -1;

    if (!enabled) {
      return -1;
    }

    long seq = System.currentTimeMillis();
    while (handlers.containsKey(seq)) {
      seq++;
    }

    try {

      /**
       * get the message producer by destination name
       */
      MessageProducer p = getQueue(dest);
      if (p != null) {

        handlers.put(seq, handler);

        BytesMessage resp = session.createBytesMessage();

        resp.writeInt(MQ.REQUEST);
        resp.writeLong(seq);
        resp.writeUTF(to);
        resp.writeUTF(from);
        resp.writeUTF(src);
        resp.writeLong(System.currentTimeMillis());

        if (header == null) {
          resp.writeInt(0);
        } else {
          ByteArrayOutputStream os = new ByteArrayOutputStream();
          ObjectOutputStream out = new ObjectOutputStream(os);
          out.writeObject(header);
          out.close();
          byte[] ss = os.toByteArray();
          resp.writeInt(ss.length);
          resp.writeBytes(os.toByteArray());
        }

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(os);
        out.writeObject(message);
        out.close();
        byte[] ss = os.toByteArray();
        resp.writeInt(ss.length);
        resp.writeBytes(os.toByteArray());

        if (bb == null) {
          resp.writeInt(0);
        } else {
          resp.writeInt(0);
          resp.writeBytes(bb);
        }

        p.send(resp, DeliveryMode.NON_PERSISTENT, 0, timeout);

        log.debug("AMQ:" + dest + ", " + message);

        synchronized (handler) {
          handler.wait(timeout);
        }

        return 1;
      }
    } catch (Exception e) {
      log.error(e.getMessage(), e);

    } finally {
      handlers.remove(seq);
    }

    return -1;
  }

  private static Map<Long, IStub> handlers = new HashMap<Long, IStub>();

  /**
   * send the message and return immediately
   * 
   * @param seq
   * @param dest
   * @param to
   * @param message
   * @param bb
   * @param src
   * @param from
   * @param header
   * @return int 1: success
   */
  public static int send(long seq, String dest, String to, JSON message, byte[] bb, String src, String from,
      JSON header) {
    if (message == null)
      return -1;

    if (!enabled) {
      return -1;
    }

    try {

      /**
       * get the message producer by destination name
       */
      MessageProducer p = getQueue(dest);
      if (p != null) {
        BytesMessage resp = session.createBytesMessage();

        // Response resp = new Response();
        resp.writeInt(MQ.REQUEST);
        resp.writeLong(seq);
        resp.writeUTF(to == null ? X.EMPTY : to);
        resp.writeUTF(from == null ? X.EMPTY : from);
        resp.writeUTF(src == null ? X.EMPTY : src);
        resp.writeLong(System.currentTimeMillis());

        if (header == null) {
          resp.writeInt(0);
        } else {
          ByteArrayOutputStream os = new ByteArrayOutputStream();
          ObjectOutputStream out = new ObjectOutputStream(os);
          out.writeObject(header);
          out.close();
          byte[] ss = os.toByteArray();
          resp.writeInt(ss.length);
          resp.writeBytes(os.toByteArray());
        }

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(os);
        out.writeObject(message);
        out.close();
        byte[] ss = os.toByteArray();
        resp.writeInt(ss.length);
        resp.writeBytes(os.toByteArray());

        if (bb == null) {
          resp.writeInt(0);
        } else {
          resp.writeInt(bb.length);
          resp.writeBytes(bb);
        }

        p.send(resp, DeliveryMode.NON_PERSISTENT, 0, X.AMINUTE);

        log.debug("AMQ:" + dest + ", " + message);

        return 1;
      }
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }

    return -1;
  }

  /**
   * response the message and return immediately
   * 
   * @param originalseq
   *          , the request seq
   * @param dest
   * @param to
   * @param message
   * @param bb
   * @param src
   * @param from
   * @param header
   * @return <br>
   *         1: success,<br>
   */
  public static int response(long originalseq, String dest, String to, JSON message, byte[] bb, String src, String from,
      JSON header) {
    if (message == null)
      return -1;

    if (!enabled) {
      return -1;
    }

    try {

      /**
       * get the message producer by destination name
       */
      MessageProducer p = getQueue(dest);
      if (p != null) {
        BytesMessage resp = session.createBytesMessage();

        resp.writeInt(MQ.RESPONSE);
        resp.writeLong(originalseq);
        resp.writeUTF(to);
        resp.writeUTF(from);
        resp.writeUTF(src);
        resp.writeLong(System.currentTimeMillis());

        if (header == null) {
          resp.writeInt(0);
        } else {
          ByteArrayOutputStream os = new ByteArrayOutputStream();
          ObjectOutputStream out = new ObjectOutputStream(os);
          out.writeObject(header);
          out.close();
          byte[] ss = os.toByteArray();
          resp.writeInt(ss.length);
          resp.writeBytes(os.toByteArray());
        }

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(os);
        out.writeObject(message);
        out.close();
        byte[] ss = os.toByteArray();
        resp.writeInt(ss.length);
        resp.writeBytes(os.toByteArray());

        if (bb == null) {
          resp.writeInt(0);
        } else {
          resp.writeInt(bb.length);
          resp.writeBytes(bb);
        }

        p.send(resp, DeliveryMode.NON_PERSISTENT, 0, X.AMINUTE);

        log.debug("response:" + dest + ", " + message);

        return 1;
      }
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }
    return -1;
  }

  /**
   * 获取消息队列的发送庄
   * 
   * @param name
   *          消息队列名称
   * @return messageproducer
   */
  private static MessageProducer getQueue(String name) {
    synchronized (queues) {
      if (enabled) {
        if (queues.containsKey(name)) {
          return queues.get(name);
        }

        try {
          Destination dest = new ActiveMQQueue(group + name);
          MessageProducer producer = session.createProducer(dest);
          producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
          queues.put(name, producer);

          return producer;
        } catch (Exception e) {
          log.error(name, e);
        }
      }
    }

    return null;
  }

  private static MessageProducer getTopic(String name) {
    synchronized (topics) {
      if (enabled) {
        if (topics.containsKey(name)) {
          return topics.get(name);
        }

        try {
          Destination dest = new ActiveMQTopic(group + name);
          MessageProducer producer = session.createProducer(dest);
          producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
          topics.put(name, producer);

          return producer;
        } catch (Exception e) {
          log.error(name, e);
        }
      }
    }
    return null;
  }

  /**
   * queue producer cache
   */
  private static Map<String, MessageProducer> queues = new HashMap<String, MessageProducer>();

  /**
   * topic producer cache
   */
  private static Map<String, MessageProducer> topics = new HashMap<String, MessageProducer>();

}
