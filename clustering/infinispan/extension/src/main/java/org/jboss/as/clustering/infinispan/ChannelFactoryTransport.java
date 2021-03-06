/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.clustering.infinispan;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.infinispan.commons.CacheException;
import org.infinispan.commons.marshall.StreamingMarshaller;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.configuration.global.TransportConfiguration;
import org.infinispan.factories.KnownComponentNames;
import org.infinispan.factories.annotations.ComponentName;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.notifications.cachemanagerlistener.CacheManagerNotifier;
import org.infinispan.remoting.inboundhandler.InboundInvocationHandler;
import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.infinispan.util.TimeService;
import org.wildfly.clustering.jgroups.spi.ChannelFactory;

/**
 * Custom {@link JGroupsTransport} that uses a provided channel.
 * @author Paul Ferraro
 */
public class ChannelFactoryTransport extends JGroupsTransport {

    private final ChannelFactory factory;

    public ChannelFactoryTransport(ChannelFactory factory) {
        this.factory = factory;
    }

    @Inject
    @Override
    public void initialize(GlobalConfiguration configuration, StreamingMarshaller marshaller,
            CacheManagerNotifier notifier, TimeService timeService, InboundInvocationHandler globalHandler,
            @ComponentName(KnownComponentNames.TIMEOUT_SCHEDULE_EXECUTOR) ScheduledExecutorService timeoutExecutor,
            @ComponentName(KnownComponentNames.REMOTE_COMMAND_EXECUTOR) ExecutorService remoteExecutor) {

        super.initialize(configuration, marshaller, notifier, timeService, globalHandler, timeoutExecutor, remoteExecutor);

        GlobalConfigurationBuilder builder = new GlobalConfigurationBuilder();
        // WFLY-6685 Prevent Infinispan from registering channel mbeans
        // The JGroups subsystem already does this
        builder.globalJmxStatistics().read(configuration.globalJmxStatistics()).disable();
        // ISPN-4755 workaround
        TransportConfiguration transport = configuration.transport();
        builder.transport()
                .clusterName(transport.clusterName())
                .distributedSyncTimeout(transport.distributedSyncTimeout())
                .initialClusterSize(transport.initialClusterSize())
                .initialClusterTimeout(transport.initialClusterTimeout(), TimeUnit.MILLISECONDS)
                .machineId(transport.machineId())
                .nodeName(transport.nodeName())
                .rackId(transport.rackId())
                .siteId(transport.siteId())
                .transport(transport.transport())
                .withProperties(transport.properties())
                ;
        this.configuration = builder.build();
    }

    @Override
    protected void initChannel() {
        try {
            this.channel = this.factory.createChannel(this.configuration.globalJmxStatistics().cacheManagerName());
            this.channel.setDiscardOwnMessages(false);
        } catch (Exception e) {
            throw new CacheException(e);
        }
    }
}
