/*******************************************************************************
 * Copyright (c) Intel Corporation
 * Copyright (c) 2017
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.osc.controller.nsfc;

import static java.util.Collections.singletonList;
import static org.osc.controller.nsfc.TestData.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient.OSClientV3;
import org.openstack4j.api.client.IOSClientBuilder.V3;
import org.openstack4j.api.networking.NetworkingService;
import org.openstack4j.api.networking.ext.ServiceFunctionChainService;
import org.openstack4j.model.network.Port;
import org.openstack4j.model.network.ext.PortPair;
import org.openstack4j.model.network.ext.PortPairGroup;
import org.osc.controller.nsfc.entities.NetworkElementImpl;
import org.osc.controller.nsfc.entities.ServiceFunctionChainElement;

public abstract class AbstractNeutronSfcPluginTest {
    @Mock
    protected V3 v3;

    @Mock
    protected OSClientV3 osClient;

    @Mock
    protected ServiceFunctionChainService sfcService;

    @Mock
    protected NetworkingService networkingService;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setup() throws Exception {
        setupDataObjects();
        Mockito.when(this.networkingService.port()).thenReturn(portService);
        Mockito.when(this.sfcService.portchains()).thenReturn(portChainService);
        Mockito.when(this.sfcService.portpairs()).thenReturn(portPairService);
        Mockito.when(this.sfcService.portpairgroups()).thenReturn(portPairGroupService);
        Mockito.when(this.sfcService.flowclassifiers()).thenReturn(flowClassifierService);
        Mockito.when(this.osClient.sfc()).thenReturn(this.sfcService);
        Mockito.when(this.osClient.networking()).thenReturn(this.networkingService);
    }

    @After
    public void tearDown() throws Exception {
    }

    protected void persistIngress() {
        ingressPort = portService.create(Builders.port().macAddress(ingressPortElement.getMacAddresses().get(0))
                                 .fixedIp(ingressPortElement.getPortIPs().get(0), "mySubnet").build());
        ingressPortElement.setElementId(ingressPort.getId());
    }

    protected void persistEgress() {
        egressPort = portService.create(Builders.port().macAddress(egressPortElement.getMacAddresses().get(0))
                                .fixedIp(egressPortElement.getPortIPs().get(0), "mySubnet").build());
        egressPortElement.setElementId(egressPort.getId());
    }

    protected void persistInspectionPort(boolean withIngress, boolean withEgress) {
        if (withIngress) {
            portPair = portPair.toBuilder().ingressId(ingressPort.getId()).build();
        }

        if (withEgress) {
            portPair = portPair.toBuilder().egressId(egressPort.getId()).build();
        }

        portPair = portPairService.create(portPair);
    }

    protected void persistPortPairGroup() {
        portPairGroup = Builders.portPairGroup()
                .portPairs(new ArrayList<>(Arrays.asList(portPair.getId())))
                .build();
        portPairGroup = portPairGroupService.create(portPairGroup);
    }

    protected void persistInspectedPort() {
        inspectedPort = portService.create(Builders.port().macAddress(inspectedPortElement.getMacAddresses().get(0))
                .fixedIp(inspectedPortElement.getPortIPs().get(0), "mySubnet").build());

        inspectedPortElement = constructNetworkElementElement(inspectedPort, null);
    }

    protected ServiceFunctionChainElement persistPortChainAndSfcElement() {

        portChain = Builders.portChain()
                .portPairGroups(singletonList(portPairGroup.getId()))
                .flowClassifiers(new ArrayList<>())
                .build();

        portChain = portChainService.create(portChain);

        ingressPortElement = constructNetworkElementElement(ingressPort, portPair.getId());
        egressPortElement = constructNetworkElementElement(egressPort, portPair.getId());
        inspectionPort.setIngressPort(ingressPortElement);
        inspectionPort.setEgressPort(egressPortElement);
        ppgElement.getPortPairs().add(inspectionPort);
        ppgElement.setElementId(portPairGroup.getId());

        sfc = new ServiceFunctionChainElement(portChain.getId());
        sfc.getPortPairGroups().add(ppgElement);
        ppgElement.setServiceFunctionChain(sfc);

        return sfc;
    }

    protected List<PortPairGroup> persistNInspectionPort(int count) {
        List<PortPairGroup> ppgList = new ArrayList<>();
        for(int i=0;i<count;i++) {
            PortPair inspPort_n = Builders.portPair().build();
            inspPort_n = portPairService.create(inspPort_n);
            PortPairGroup ppg_n= Builders.portPairGroup()
                                    .portPairs(singletonList(inspPort_n.getId())).build();
            ppg_n = portPairGroupService.create(ppg_n);
            ppgList.add(ppg_n);
        }
        return ppgList;
    }

    private NetworkElementImpl constructNetworkElementElement(Port port, String parentId) {
        List<String> ips;
        if (port.getFixedIps() != null) {
            ips = port.getFixedIps().stream().map(ip -> ip.getIpAddress()).collect(Collectors.toList());
        } else {
            ips = new ArrayList<>();
        }
        return new NetworkElementImpl(port.getId(), singletonList(port.getMacAddress()), ips, parentId);
    }
}
