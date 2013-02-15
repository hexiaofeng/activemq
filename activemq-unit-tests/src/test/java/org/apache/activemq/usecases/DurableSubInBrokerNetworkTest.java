/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.usecases;

import java.net.URI;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

import javax.management.ObjectName;

import junit.framework.Assert;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.network.DiscoveryNetworkConnector;
import org.apache.activemq.network.NetworkConnector;
import org.apache.activemq.network.NetworkTestSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests durable topic subscriptions inside a network of brokers.
 * 
 * @author tmielke
 *
 */
public class DurableSubInBrokerNetworkTest extends NetworkTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(NetworkConnector.class);
    // protected BrokerService localBroker;
    private final String subName = "Subscriber1";
    private final String topicName = "TEST.FOO";

    protected void setUp() throws Exception {
        useJmx=true;
        super.setUp();

        URI ncUri = new URI("static:(" + connector.getConnectUri().toString() + ")");
        NetworkConnector nc = new DiscoveryNetworkConnector(ncUri);
        nc.setDuplex(true);
        remoteBroker.addNetworkConnector(nc);
        nc.start();
    }

    protected void tearDown() throws Exception {
        if (remoteBroker.isStarted()) {
            remoteBroker.stop();
            remoteBroker.waitUntilStopped();
        }
        if (broker.isStarted()) {
            broker.stop();
            broker.waitUntilStopped();
        }
        super.tearDown();
    }


    /**
     * Creates a durable topic subscription, checks that it is propagated
     * in the broker network, removes the subscription and checks that
     * the subscription is removed from remote broker as well.
     *  
     * @throws Exception
     */
    public void testDurableSubNetwork() throws Exception {
        LOG.info("testDurableSubNetwork started.");

        // create durable sub
        ActiveMQConnectionFactory fact = new ActiveMQConnectionFactory(connector.getConnectUri().toString());
        Connection conn = fact.createConnection();
        conn.setClientID("clientID1");
        Session session = conn.createSession(false, 1);
        Destination dest = session.createTopic(topicName);
        TopicSubscriber sub = session.createDurableSubscriber((Topic)dest, subName);
        LOG.info("Durable subscription of name " + subName + "created.");
        Thread.sleep(100);

        // query durable sub on local and remote broker
        // raise an error if not found
        boolean foundSub = false;
        ObjectName[] subs = broker.getAdminView().getDurableTopicSubscribers();
        
        for (int i=0 ; i<subs.length; i++) {
            if (subs[i].toString().contains(subName))
                foundSub = true;
        }
        Assert.assertTrue(foundSub);

        foundSub = false;
        subs = remoteBroker.getAdminView().getDurableTopicSubscribers();
        for (int i=0 ; i<subs.length; i++) {
            if (subs[i].toString().contains("destinationName=" + topicName))
                foundSub = true;
        }
        Assert.assertTrue(foundSub);

        // unsubscribe from durable sub
        sub.close();
        session.unsubscribe(subName);
        LOG.info("Unsubscribed from durable subscription.");
        Thread.sleep(100);

        // query durable sub on local and remote broker
        // raise an error if its not removed from both brokers
        foundSub = false;
        subs = broker.getAdminView().getDurableTopicSubscribers();
        for (int i=0 ; i<subs.length; i++) {
            if (subs[i].toString().contains(subName))
                foundSub = true;
        }
        Assert.assertFalse(foundSub);

        foundSub = false;
        subs = remoteBroker.getAdminView().getDurableTopicSubscribers();
        for (int i=0 ; i<subs.length; i++) {
            if (subs[i].toString().contains("destinationName=" + topicName))
                foundSub = true;
        }
        Assert.assertFalse("Durable subscription not unregistered on remote broker", foundSub);


    }
}