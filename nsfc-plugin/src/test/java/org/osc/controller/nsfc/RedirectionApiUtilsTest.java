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
import static org.junit.Assert.*;
import static org.osc.controller.nsfc.TestData.*;

import java.util.ArrayList;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.openstack4j.api.Builders;
import org.openstack4j.model.network.Port;
import org.openstack4j.model.network.ext.FlowClassifier;
import org.openstack4j.model.network.ext.PortChain;
import org.openstack4j.model.network.ext.PortPair;
import org.openstack4j.model.network.ext.PortPairGroup;
import org.osc.controller.nsfc.entities.NetworkElementImpl;
import org.osc.controller.nsfc.utils.OsCalls;
import org.osc.controller.nsfc.utils.RedirectionApiUtils;

@RunWith(MockitoJUnitRunner.class)
public class RedirectionApiUtilsTest extends AbstractNeutronSfcPluginTest {

    private OsCalls osCalls;

    private RedirectionApiUtils utils;

    @Before
    @Override
    public void setup() throws Exception {
        super.setup();

        this.osCalls = new OsCalls(this.osClient);
        this.utils = new RedirectionApiUtils(this.osCalls);
    }

    @Test
    public void testUtils_FetchProtectedPort_PortExists_Success() throws Exception {

        // Arrange.
        persistInspectedPort();

        FlowClassifier flowClassifier = Builders.flowClassifier().logicalDestinationPort(inspectedPortElement.getElementId()).build();
        flowClassifier = this.osClient.sfc().flowclassifiers().create(flowClassifier);

        // Act.
        Port foundPort = this.utils.fetchProtectedPort(flowClassifier);

        //Assert.
        assertNotNull(foundPort);
        assertEquals(inspectedPort.getId(), foundPort.getId());
    }

    @Test
    public void testUtils_FetchPortPairByNetworkElements_BothRightPorts_Success() throws Exception {

        // Arrange.
        persistIngress();
        persistEgress();

        boolean withIngress = true;
        boolean withEgress = true;
        persistInspectionPort(withIngress, withEgress);

        // Act.
        PortPair foundPortPair = this.utils.fetchPortPairByNetworkElements(ingressPortElement, egressPortElement);

        // Assert.
        assertNotNull(foundPortPair);
        assertEquals(portPair.getId(), foundPortPair.getId());
        assertEquals(portPair.getIngressId(), ingressPort.getId());
        assertEquals(portPair.getEgressId(), egressPort.getId());
    }

    @Test
    public void testUtils_FetchPortPairByNetworkElements_NoSuchPair_ReturnsNull() throws Exception {

        // Arrange.
        persistIngress();
        persistEgress();

        boolean withIngress = true;
        boolean withEgress = true;
        persistInspectionPort(withIngress, withEgress);

        NetworkElementImpl other = new NetworkElementImpl("nosuchthing", null, null, null);

        // Act
        PortPair foundByIngr = this.utils.fetchPortPairByNetworkElements(ingressPortElement, other);

        // Assert.
        assertNull(foundByIngr);

        // Act
        PortPair foundByEgr = this.utils.fetchPortPairByNetworkElements(other, egressPortElement);

        // Assert.
        assertNull(foundByEgr);

        // Act
        PortPair foundByWrongPort = this.utils.fetchPortPairByNetworkElements(other, other);

        // Assert.
        assertNull(foundByWrongPort);
    }

    @Test
    public void testUtils_FetchContainingPortPairGroup_PortPairGroupExists_Success() throws Exception {

        // Arrange.
        persistIngress();
        persistEgress();

        boolean withIngress = true;
        boolean withEgress = true;
        persistInspectionPort(withIngress, withEgress);
        persistPortPairGroup();

        // Act
        PortPairGroup foundPortPairGroup = this.utils.fetchContainingPortPairGroup(portPair.getId());

        // Assert.
        assertNotNull(foundPortPairGroup);
        assertEquals(portPairGroup.getId(), foundPortPairGroup.getId());
    }

    @Test
    public void testUtils_FetchContainingPortPairGroup_NoSuchGroup_ReturnsNull() throws Exception {

        // Arrange.
        persistIngress();
        persistEgress();

        boolean withIngress = true;
        boolean withEgress = true;
        persistInspectionPort(withIngress, withEgress);

        // don't
        // persistPortPairGroup();

        // Act
        PortPairGroup foundPortPairGroup = this.utils.fetchContainingPortPairGroup(portPair.getId());

        // Assert.
        assertNull(foundPortPairGroup);
    }

    @Test
    public void testUtils_FetchContainingPortChain_ChainExists_Success() throws Exception {

        // Arrange.
        persistIngress();
        persistEgress();

        boolean withIngress = true;
        boolean withEgress = true;
        persistInspectionPort(withIngress, withEgress);

        portChain = Builders.portChain()
                .portPairGroups(singletonList(portPairGroup.getId()))
                .flowClassifiers(new ArrayList<>())
                .build();

        portChain = portChainService.create(portChain);

        // Act
        PortChain foundPortChain = this.utils.fetchContainingPortChain(portPairGroup.getId());

        // Assert.
        assertNotNull(foundPortChain);
        assertEquals(portChain.getId(), foundPortChain.getId());
    }

    @Test
    public void testUtils_FetchContainingPortChain_NoSuchChain_ReturnsNull() throws Exception {

        // Arrange.
        persistIngress();
        persistEgress();

        boolean withIngress = true;
        boolean withEgress = true;
        persistInspectionPort(withIngress, withEgress);

        // Act
        PortChain foundPortChain = this.utils.fetchContainingPortChain(portPairGroup.getId());

        // Assert.
        assertNull(foundPortChain);
    }

    @Test
    public void testUtils_BuildFlowClassifier_NonNullInspectedPortId_Success() throws Exception {

        // Arrange.
        String inspectedPortId = "somethingorother";
        String defaultGatewayPortId = "something";
        // Act
        FlowClassifier flowClassifier = this.utils.buildFlowClassifier(inspectedPortId, defaultGatewayPortId);

        // Assert.
        assertNotNull(flowClassifier);
        assertEquals(inspectedPortId, flowClassifier.getLogicalDestinationPort());
        assertNotNull(flowClassifier.getName());
        assertTrue(flowClassifier.getName().startsWith("OSCFlowClassifier-"));
    }
}
