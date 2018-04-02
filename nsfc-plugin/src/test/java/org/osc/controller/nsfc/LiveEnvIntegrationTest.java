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

import static java.util.Arrays.asList;
import static org.junit.Assert.*;
import static org.osc.sdk.controller.FailurePolicyType.NA;
import static org.osc.sdk.controller.TagEncapsulationType.VLAN;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient.OSClientV3;
import org.openstack4j.api.client.IOSClientBuilder.V3;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.network.Port;
import org.openstack4j.model.network.ext.FlowClassifier;
import org.openstack4j.model.network.ext.PortChain;
import org.openstack4j.model.network.ext.PortPair;
import org.openstack4j.model.network.ext.PortPairGroup;
import org.openstack4j.openstack.OSFactory;
import org.osc.controller.nsfc.api.NeutronSfcSdnControllerApi;
import org.osc.controller.nsfc.api.NeutronSfcSdnRedirectionApi;
import org.osc.controller.nsfc.entities.NetworkElementImpl;
import org.osc.controller.nsfc.entities.PortPairElement;
import org.osc.controller.nsfc.entities.ServiceFunctionChainElement;
import org.osc.controller.nsfc.utils.OsCalls;
import org.osc.sdk.controller.api.SdnControllerApi;
import org.osc.sdk.controller.api.SdnRedirectionApi;
import org.osc.sdk.controller.element.Element;
import org.osc.sdk.controller.element.InspectionHookElement;
import org.osc.sdk.controller.element.NetworkElement;
import org.osc.sdk.controller.element.VirtualizationConnectorElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This test is run against live ocata environment. Username and password should work.
 *
 * The name of Project (AKA Tenant) should be admin.
 *
 * INGRESS/EGRESS ID's, MAC's and IP's should correspond to pre-created VM's on ocata.
 *
 * Due to certain issues with networking-sfc plugin and openvswitch, we have to make certain
 * temporary concessions. We are not really using the ovs driver. On control node,
 * use 'drivers = dummy' in /etc/neutron/neutron.conf. That's under the [ovs] section.
 *
 * On the compute node, comment out the "extensions = sfc" line in
 * /etc/neutron/plugins/ml2/openvswitch_agent.ini. Effectively, this turns off hte actual
 * networking_sfc agent and only tests the plugin, making sure the api calls are correct and
 * in the correct sequence.
 *
 * See https://docs.openstack.org/networking-sfc/latest/install/index.html
 *
 */
public class LiveEnvIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(LiveEnvIntegrationTest.class);

    private static final String DOMAIN_NAME = "default";
    private static final String PASSWORD = "admin123";
    private static final String USERNAME = "admin";
    private static final String TENANT = "admin";
    private static final String TEST_CONTROLLER_IP = "10.3.243.183";

    private static final String INGRESS0_ID = "fb2da712-637a-4fb1-a0eb-b5db78639870";
    private static final String EGRESS0_ID = "fb2da712-637a-4fb1-a0eb-b5db78639870";

    private static final String INGRESS0_IP = "172.16.3.5";
    private static final String EGRESS0_IP = "172.16.3.5";

    private static final String INGRESS0_MAC = "fa:16:3e:a8:01:06";
    private static final String EGRESS0_MAC = "fa:16:3e:a8:01:06";

    private static final String INGRESS1_ID = "e6ebf285-07e2-4de1-aa7f-c65950784f13";
    private static final String EGRESS1_ID = "e6ebf285-07e2-4de1-aa7f-c65950784f13";

    private static final String INGRESS1_IP = "172.16.1.5";
    private static final String EGRESS1_IP = "172.16.1.5";

    private static final String INGRESS1_MAC = "fa:16:3e:91:80:69";
    private static final String EGRESS1_MAC = "fa:16:3e:91:80:69";

    private static final String INSPECTED_ID = "11e5dd85-b1f7-422d-a822-181351b22aef";
    private static final String INSPECTED_IP = "172.16.3.8";
    private static final String INSPECTED_MAC = "fa:16:3e:ca:37:38";

    // just for verifying stuff
    private OSClientV3 osClient;
    private OsCalls osCalls;
    private NetworkElementImpl ingressElement0;
    private NetworkElementImpl egressElement0;
    private PortPairElement inspectionPortElement0;
    private NetworkElementImpl ingressElement1;
    private NetworkElementImpl egressElement1;
    private PortPairElement inspectionPortElement1;

    SdnControllerApi api;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private SdnRedirectionApi redirApi;

    private static final VirtualizationConnectorElement VC =
            new VirtualizationConnectorElement() {

                @Override
                public String getName() {
                    return "dummy";
                }

                @Override
                public String getControllerIpAddress() {
                    return "dummy";                }

                @Override
                public String getControllerUsername() {
                    return "dummy";                }

                @Override
                public String getControllerPassword() {
                    return "dummy";                }

                @Override
                public boolean isControllerHttps() {
                    return false;
                }

                @Override
                public String getProviderIpAddress() {
                    return TEST_CONTROLLER_IP;       }

                @Override
                public String getProviderUsername() {
                    return USERNAME;                }

                @Override
                public String getProviderPassword() {
                    return PASSWORD;                }

                @Override
                public String getProviderAdminTenantName() {
                    return TENANT;                }

                @Override
                public String getProviderAdminDomainId() {
                    return DOMAIN_NAME;                }

                @Override
                public boolean isProviderHttps() {
                    return false;
                }

                @Override
                public Map<String, String> getProviderAttributes() {
                    return null;
                }

                @Override
                public SSLContext getSslContext() {
                    return null;
                }

                @Override
                public TrustManager[] getTruststoreManager() throws Exception {
                    return null;
                }
    };

    @Before
    public void setup() {
        String domain = VC.getProviderAdminDomainId();
        String username = VC.getProviderUsername();
        String password = VC.getProviderPassword();
        String tenantName = VC.getProviderAdminTenantName();

        V3 v3 = OSFactory.builderV3()
                .endpoint("http://" + VC.getProviderIpAddress() + ":5000/v3")
                .credentials(username, password, Identifier.byName(domain))
                .scopeToProject(Identifier.byName(tenantName), Identifier.byName(domain));

        this.osClient = v3.authenticate();
        this.osCalls = new OsCalls(this.osClient);

        this.api =  new NeutronSfcSdnControllerApi();

        this.ingressElement0 = new NetworkElementImpl(INGRESS0_ID, asList(INGRESS0_MAC),
                                                                    asList(INGRESS0_IP), null);
        this.egressElement0 = new NetworkElementImpl(EGRESS0_ID, asList(EGRESS0_MAC),
                                                                  asList(EGRESS0_IP), null);
        this.inspectionPortElement0 = new PortPairElement(null, null, this.ingressElement0, this.egressElement0);

        this.ingressElement1 = new NetworkElementImpl(INGRESS1_ID, asList(INGRESS1_MAC),
                                                                    asList(INGRESS1_IP), null);
        this.egressElement1 = new NetworkElementImpl(EGRESS1_ID, asList(EGRESS1_MAC),
                       asList(EGRESS1_IP), null);
        this.inspectionPortElement1 = new PortPairElement(null, null, this.ingressElement1, this.egressElement1);
    }

    @After
    public void tearDown() throws Exception {
        if (this.redirApi != null) {
            this.redirApi.close();
        }
        cleanAllOnOpenstack();
    }

//  //@Test
    public void verifyApiResponds() throws Exception {
        // Act.
        this.redirApi = this.api.createRedirectionApi(VC, "DummyRegion");
        InspectionHookElement noSuchHook = this.redirApi.getInspectionHook("No shuch hook");

        // Assert.
        assertNull(noSuchHook);
        assertTrue(this.redirApi instanceof NeutronSfcSdnRedirectionApi);
    }

//    //@Test
    public void testPortPairsWorkflow() throws Exception {
        this.redirApi = this.api.createRedirectionApi(VC, "DummyRegion");

        // TEST CALL
        Element result0 = this.redirApi.registerInspectionPort(this.inspectionPortElement0);

        assertNotNull(result0);
        LOG.debug("Success registering inspection port {} (Actual class {})", result0.getElementId(), result0.getClass());
        this.inspectionPortElement0 = (PortPairElement) result0;

        assertPortPairGroupIsOk(this.inspectionPortElement0);
        assertIngressEgressOk(this.inspectionPortElement0);

        // same parent
        this.inspectionPortElement1.setPortPairGroup(this.inspectionPortElement0.getPortPairGroup());

        // TEST CALL
        Element result1 = this.redirApi.registerInspectionPort(this.inspectionPortElement1);

        assertNotNull(result1);
        LOG.debug("Success registering inspection port {} (Actual class {})", result1.getElementId(), result1.getClass());
        this.inspectionPortElement1 = (PortPairElement) result1;

        assertEquals(this.inspectionPortElement0.getParentId(), this.inspectionPortElement1.getParentId());
        assertPortPairGroupIsOk(this.inspectionPortElement1);
        assertIngressEgressOk(this.inspectionPortElement0);

        // TEST CALL
        this.redirApi.removeInspectionPort(this.inspectionPortElement0);

        assertNull(this.osClient.sfc().portpairs().get(this.inspectionPortElement0.getElementId()));
        assertNotNull(this.osClient.sfc().portpairs().get(this.inspectionPortElement1.getElementId()));
        assertNotNull(this.osClient.sfc().portpairgroups().get(this.inspectionPortElement0.getParentId()));

        // TEST CALL
        this.redirApi.removeInspectionPort(this.inspectionPortElement1);

        assertNull(this.osClient.sfc().portpairs().get(this.inspectionPortElement1.getElementId()));
        assertNull(this.osClient.sfc().portpairgroups().get(this.inspectionPortElement1.getParentId()));
    }

//    //@Test
    public void testInspectionHooksWorkflow_BothPairsInSamePPG() throws Exception {
        this.redirApi = this.api.createRedirectionApi(VC, "DummyRegion");

        Element result0 = this.redirApi.registerInspectionPort(this.inspectionPortElement0);
        this.inspectionPortElement0 = (PortPairElement) result0;

        // same parent
        this.inspectionPortElement1.setPortPairGroup(this.inspectionPortElement0.getPortPairGroup());

        Element result1 = this.redirApi.registerInspectionPort(this.inspectionPortElement1);
        this.inspectionPortElement1 = (PortPairElement) result1;

        // TEST CALL
        NetworkElement ne = this.redirApi.registerNetworkElement(asList(this.inspectionPortElement0.getPortPairGroup()));
        ServiceFunctionChainElement sfc = (ServiceFunctionChainElement) ne;

        NetworkElementImpl inspected = new NetworkElementImpl(INSPECTED_ID, asList(INSPECTED_MAC),
                                                                  asList(INSPECTED_IP), null);

        // TEST CALL
        String hookId = this.redirApi.installInspectionHook(inspected, sfc, 0L, VLAN, 0L, NA);

        assertNotNull(hookId);

        // TEST CALL
        InspectionHookElement ih = this.redirApi.getInspectionHook(hookId);

        assertNotNull(ih);

        Port inspectedPortCheck = this.osClient.networking().port().get(INSPECTED_ID);
        assertNotNull(inspectedPortCheck);

        String sfcId = ih.getInspectionPort().getElementId();
        assertEquals(sfc.getElementId(), sfcId);

        ServiceFunctionChainElement sfcElementCheck = (ServiceFunctionChainElement) ih.getInspectionPort();
        assertEquals(sfc.getElementId(), sfcElementCheck.getElementId());
        assertNotNull(sfcElementCheck.getInspectionHooks());
        assertEquals(1, sfcElementCheck.getInspectionHooks().size());
        assertEquals(hookId, sfcElementCheck.getInspectionHooks().iterator().next().getHookId());

        PortChain portChainCheck = this.osClient.sfc().portchains().get(sfcId);
        assertNotNull(portChainCheck);

        FlowClassifier flowClassifierCheck = this.osClient.sfc().flowclassifiers().get(hookId);
        assertNotNull(flowClassifierCheck);
        assertTrue(portChainCheck.getFlowClassifiers().contains(hookId));

        // TEST CALL
        this.redirApi.removeInspectionHook(hookId);

        assertNull(this.redirApi.getInspectionHook(hookId));
        assertNull(this.osClient.sfc().flowclassifiers().get(hookId));

        portChainCheck = this.osClient.sfc().portchains().get(sfcId);
        assertFalse(portChainCheck.getFlowClassifiers() != null
                          && portChainCheck.getFlowClassifiers().contains(hookId));

        // TEST CALL
        this.redirApi.deleteNetworkElement(sfc);
        assertNull(this.osClient.sfc().portchains().get(sfc.getElementId()));
    }

    private static final String NONEXISTENT_ID = "11e5aa85-a1a7-555a-a822-181351b22aef";

    //@Test
    public void attemptMakePortPairFromPortsOnDifferentVMS() {
        try {
            PortPair portPair = Builders.portPair().ingressId(INGRESS0_ID).egressId(INSPECTED_ID).build();
            this.osClient.sfc().portpairs().create(portPair);
            Assert.fail();
        } catch (Exception e) {
            printThrowable(e);
        }
    }

    //@Test
    public void attemptMakePortPairFromPortsWithNonexistentVMS() {
        PortPair portPair = Builders.portPair().ingressId(INGRESS0_ID).egressId(NONEXISTENT_ID).build();
        portPair = this.osClient.sfc().portpairs().create(portPair);
        assertNull(portPair);
    }

    //@Test
    public void attemptMakePortPairGroupNonexPortPair() {
        PortPairGroup portPairGroup = Builders.portPairGroup().name("attemptMakePortPairGroupNonexPortPair").portPairs(Arrays.asList(NONEXISTENT_ID)).build();
        portPairGroup = this.osClient.sfc().portpairgroups().create(portPairGroup);
        // Nonexistent port pair works here but not in neutron command line
    }

    //@Test
    public void attemptMakePortPairGroupNullPortPair() {
        PortPairGroup portPairGroup = Builders.portPairGroup().name("attemptMakePortPairGroupNullPortPair").build();
        portPairGroup = this.osClient.sfc().portpairgroups().create(portPairGroup);
        // Empty port pair is OK
    }

    //@Test
    public void attemptMakePortChainNonexPPG() {
        PortChain portChain = Builders.portChain().portPairGroups(Arrays.asList(NONEXISTENT_ID)).build();
        portChain = this.osClient.sfc().portchains().create(portChain);
        // Nonexistent port pair works here but not in neutron command line
    }

    //@Test
    public void attemptMakePortChainNullPPG() {
        try {
            PortChain portChain = Builders.portChain().build();
            portChain = this.osClient.sfc().portchains().create(portChain);
            Assert.fail();
        } catch (Exception e) {
            printThrowable(e);
        }
    }

    //@Test
    public void attemptUpdatePortPairFromPortsOnDifferentVMS() {
        try {
            PortPair portPair = Builders.portPair().ingressId(INGRESS0_ID).egressId(INGRESS0_ID).build();
            portPair = this.osClient.sfc().portpairs().create(portPair);
            String id = portPair.getId();
            assertNotNull(id);

            portPair = portPair.toBuilder().egressId(NONEXISTENT_ID).id(null).build();
            portPair = this.osClient.sfc().portpairs().update(id, portPair);
            Assert.fail();
        } catch (Exception e) {
            printThrowable(e);
        }
    }

    // The following calls simply our assumptions about the REST API

    //@Test
    public void deletingNonexistentFlowClassifierThroughWrapperOk() {
        this.osCalls.deleteFlowClassifier(NONEXISTENT_ID);
    }
    //@Test
    public void deletingNonexistentPortChainThroughWrapperOk() {
        this.osCalls.deletePortChain(NONEXISTENT_ID);
    }
    //@Test
    public void deletingNonexistentPortPairGroupThroughWrapperOk() {
        this.osCalls.deletePortPairGroup(NONEXISTENT_ID);
    }
    //@Test
    public void deletingNonexistentPortPairThroughWrapperOk() {
        this.osCalls.deletePortPair(NONEXISTENT_ID);
    }

    //@Test
    public void deleteNonexistentFlowClassifierThroughApi404() {
        ActionResponse response = this.osClient.sfc().flowclassifiers().delete(NONEXISTENT_ID);
        assertEquals(404, response.getCode());
    }
    //@Test
    public void deleteNonexistentPortChainThroughApi404() {
        ActionResponse response = this.osClient.sfc().portchains().delete(NONEXISTENT_ID);
        assertEquals(404, response.getCode());
    }
    //@Test
    public void deleteNonexistentPortPairGroupThroughApi404() {
        ActionResponse response = this.osClient.sfc().portpairgroups().delete(NONEXISTENT_ID);
        assertEquals(404, response.getCode());
    }
    //@Test
    public void deleteNonexistentPortPairThroughApi404() {
        ActionResponse response = this.osClient.sfc().portpairs().delete(NONEXISTENT_ID);
        assertEquals(404, response.getCode());
    }

    private void printThrowable(Throwable e) {
        LOG.error("Received exception: {}: {}", e.getClass(), e.getMessage());
    }

    //@Test
    public void cleanAllOnOpenstack() {
        List<? extends PortChain> portChains = this.osClient.sfc().portchains().list();
        List<? extends FlowClassifier> flowClassifiers = this.osClient.sfc().flowclassifiers().list();
        List<? extends PortPairGroup> portPairGroups = this.osClient.sfc().portpairgroups().list();
        List<? extends PortPair> portPairs = this.osClient.sfc().portpairs().list();

        for (PortChain pc : portChains) {
            this.osClient.sfc().portchains().delete(pc.getId());
        }
        for (FlowClassifier fc : flowClassifiers) {
            this.osClient.sfc().flowclassifiers().delete(fc.getId());
        }
        for (PortPairGroup ppg : portPairGroups) {
            this.osClient.sfc().portpairgroups().delete(ppg.getId());
        }
        for (PortPair pp : portPairs) {
            this.osClient.sfc().portpairs().delete(pp.getId());
        }

        assertEquals("Failed clean port chains!", 0, this.osClient.sfc().portchains().list().size());
        assertEquals("Failed clean flow classifiers!", 0, this.osClient.sfc().flowclassifiers().list().size());
        assertEquals("Failed clean port pair groups!", 0, this.osClient.sfc().portpairgroups().list().size());
        assertEquals("Failed clean port pairs!", 0, this.osClient.sfc().portpairs().list().size());
    }

    private void assertIngressEgressOk(PortPairElement inspectionPortElement) {
        PortPair portPairCheck = this.osClient.sfc().portpairs().get(inspectionPortElement.getElementId());
        assertEquals(inspectionPortElement.getEgressPort().getElementId(), portPairCheck.getEgressId());
        assertEquals(inspectionPortElement.getEgressPort().getElementId(), portPairCheck.getEgressId());
    }

    private void assertPortPairGroupIsOk(PortPairElement inspectionPortElement) {
        assertNotNull(inspectionPortElement.getPortPairGroup());
        PortPairGroup ppgCheck = this.osClient.sfc().portpairgroups().get(inspectionPortElement.getParentId());
        assertNotNull(ppgCheck);
        assertNotNull(ppgCheck.getPortPairs());
        assertTrue(ppgCheck.getPortPairs().contains(inspectionPortElement.getElementId()));
    }
}
