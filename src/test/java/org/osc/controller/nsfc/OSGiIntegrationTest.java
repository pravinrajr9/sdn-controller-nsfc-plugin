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
import static java.util.Collections.singletonMap;
import static org.junit.Assert.*;
import static org.ops4j.pax.exam.CoreOptions.*;
import static org.osc.sdk.controller.FailurePolicyType.NA;
import static org.osc.sdk.controller.TagEncapsulationType.VLAN;
import static org.osgi.service.jdbc.DataSourceFactory.*;

import java.io.File;
import java.sql.SQLException;
import java.util.Properties;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.sql.DataSource;

import org.hamcrest.core.StringStartsWith;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.PathUtils;
import org.osc.controller.nsfc.api.NeutronSfcSdnRedirectionApi;
import org.osc.controller.nsfc.entities.InspectionHookEntity;
import org.osc.controller.nsfc.entities.InspectionPortEntity;
import org.osc.controller.nsfc.entities.NetworkElementEntity;
import org.osc.controller.nsfc.entities.PortPairGroupEntity;
import org.osc.controller.nsfc.entities.ServiceFunctionChainEntity;
import org.osc.controller.nsfc.utils.RedirectionApiUtils;
import org.osc.sdk.controller.api.SdnControllerApi;
import org.osc.sdk.controller.element.Element;
import org.osc.sdk.controller.element.InspectionHookElement;
import org.osc.sdk.controller.element.InspectionPortElement;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.jdbc.DataSourceFactory;
import org.osgi.service.jpa.EntityManagerFactoryBuilder;
import org.osgi.service.transaction.control.TransactionControl;
import org.osgi.service.transaction.control.jpa.JPAEntityManagerProviderFactory;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class OSGiIntegrationTest {

    private static final String TEST_DB_URL_PREFIX = "jdbc:h2:";
    private static final String TEST_DB_FILENAME = "./nsfcPlugin_OSGiIntegrationTest";
    private static final String TEST_DB_URL_SUFFIX = ";MVCC\\=FALSE;LOCK_TIMEOUT\\=10000;MV_STORE=FALSE;";
    private static final String TEST_DB_URL = TEST_DB_URL_PREFIX + TEST_DB_FILENAME + TEST_DB_URL_SUFFIX;

    private static final String EADDR2_STR = "192.168.0.12";

    private static final String EADDR1_STR = "192.168.0.11";

    private static final String IADDR2_STR = "10.4.3.2";

    private static final String IADDR1_STR = "10.4.3.1";

    private static final String EMAC2_STR = "ee:ff:aa:bb:cc:02";

    private static final String EMAC1_STR = "ee:ff:aa:bb:cc:01";

    private static final String IMAC1_STR = "ff:ff:aa:bb:cc:01";

    private static final String IMAC2_STR = "ff:ff:aa:bb:cc:02";

    private static final String INSPMAC1_STR = "aa:aa:aa:bb:cc:01";

    @Inject
    BundleContext context;

    @Inject
    SdnControllerApi api;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private TransactionControl txControl;
    private EntityManagerFactoryBuilder builder;
    private DataSourceFactory jdbcFactory;
    private JPAEntityManagerProviderFactory resourceFactory;

    private EntityManager em;

    private InspectionHookEntity inspectionHook;
    private InspectionPortEntity inspectionPort;
    private PortPairGroupEntity ppg;
    private ServiceFunctionChainEntity sfc;

    private NetworkElementEntity ingress;
    private NetworkElementEntity egress;
    private NetworkElementEntity inspected;

    private NeutronSfcSdnRedirectionApi redirApi;

    @org.ops4j.pax.exam.Configuration
    public Option[] config() {

        try {
            return options(

                    // Load the current module from its built classes so we get
                    // the latest from Eclipse
                    bundle("reference:file:" + PathUtils.getBaseDir() + "/target/classes/"),
                    // And some dependencies
                    mavenBundle("org.apache.felix", "org.apache.felix.scr").versionAsInProject(),

                    mavenBundle("org.osc.api", "sdn-controller-api").versionAsInProject(),

                    mavenBundle("org.osgi", "org.osgi.core").versionAsInProject(),

                    mavenBundle("org.apache.aries.jpa", "org.apache.aries.jpa.container").versionAsInProject(),
                    mavenBundle("org.apache.aries.tx-control", "tx-control-service-local").versionAsInProject(),
                    mavenBundle("org.apache.aries.tx-control", "tx-control-provider-jpa-local").versionAsInProject(),
                    mavenBundle("com.h2database", "h2").versionAsInProject(),

                    // Hibernate

                    systemPackage("javax.xml.stream;version=1.0"), systemPackage("javax.xml.stream.events;version=1.0"),
                    systemPackage("javax.xml.stream.util;version=1.0"), systemPackage("javax.transaction;version=1.1"),
                    systemPackage("javax.transaction.xa;version=1.1"),

                    mavenBundle("org.apache.servicemix.bundles", "org.apache.servicemix.bundles.antlr")
                            .versionAsInProject(),
                    mavenBundle("org.apache.servicemix.bundles", "org.apache.servicemix.bundles.dom4j")
                            .versionAsInProject(),
                    mavenBundle("org.javassist", "javassist").versionAsInProject(),
                    mavenBundle("org.jboss.logging", "jboss-logging").versionAsInProject(),
                    mavenBundle("org.jboss", "jandex").versionAsInProject(),

                    mavenBundle("org.hibernate.common", "hibernate-commons-annotations").versionAsInProject(),
                    mavenBundle("org.hibernate", "hibernate-core").versionAsInProject(),
                    mavenBundle("org.hibernate", "hibernate-osgi").versionAsInProject(),
                    mavenBundle("com.fasterxml", "classmate").versionAsInProject(),
                    mavenBundle("org.javassist", "javassist").versionAsInProject(),

                    mavenBundle("org.slf4j", "slf4j-api").versionAsInProject(),
                    mavenBundle("ch.qos.logback", "logback-core").versionAsInProject(),
                    mavenBundle("ch.qos.logback", "logback-classic").versionAsInProject(),

                    mavenBundle("org.apache.directory.studio", "org.apache.commons.lang").versionAsInProject(),

                    // Uncomment this line to allow remote debugging
//                    CoreOptions.vmOption("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=1047"),

                    bootClasspathLibrary(mavenBundle("org.apache.geronimo.specs", "geronimo-jta_1.1_spec", "1.1.1"))
                            .beforeFramework(),
                    junitBundles());
        } catch (Throwable t) {

            System.err.println(t.getClass().getName() + ":\n" + t.getMessage());
            t.printStackTrace(System.err);
            throw t;
        }
    }

    @Before
    public void setup() {

        ServiceReference<DataSourceFactory> dsRef = this.context.getServiceReference(DataSourceFactory.class);
        this.jdbcFactory = this.context.getService(dsRef);

        ServiceReference<EntityManagerFactoryBuilder> emRef = this.context
                .getServiceReference(EntityManagerFactoryBuilder.class);
        this.builder = this.context.getService(emRef);

        ServiceReference<TransactionControl> txcRef = this.context.getServiceReference(TransactionControl.class);
        this.txControl = this.context.getService(txcRef);

        ServiceReference<JPAEntityManagerProviderFactory> jpaRef = this.context
                .getServiceReference(JPAEntityManagerProviderFactory.class);
        this.resourceFactory = this.context.getService(jpaRef);

        assertNotNull(this.jdbcFactory);
        assertNotNull(this.builder);
        assertNotNull(this.txControl);
        assertNotNull(this.resourceFactory);

        Properties props = new Properties();

        props.setProperty(JDBC_URL, TEST_DB_URL);
        props.setProperty(JDBC_USER, "admin");
        props.setProperty(JDBC_PASSWORD, "admin123");

        DataSource ds = null;
        try {
            ds = this.jdbcFactory.createDataSource(props);
        } catch (SQLException e) {
            Assert.fail(e.getClass() + " : " + e.getMessage());
        }

        this.em = this.resourceFactory
                .getProviderFor(this.builder, singletonMap("javax.persistence.nonJtaDataSource", (Object) ds), null)
                .getResource(this.txControl);

        assertNotNull(this.em);

        setupDataObjects();

    }

    private void setupDataObjects() {
        this.ingress = new NetworkElementEntity();
        this.ingress.setElementId(IMAC1_STR + IMAC1_STR);
        this.ingress.setMacAddresses(asList(IMAC1_STR, IMAC2_STR));
        this.ingress.setPortIPs(asList(IADDR1_STR, IADDR2_STR));

        this.egress = new NetworkElementEntity();
        this.egress.setElementId(EMAC1_STR + EMAC1_STR);
        this.egress.setMacAddresses(asList(EMAC1_STR, EMAC2_STR));
        this.egress.setPortIPs(asList(EADDR1_STR, EADDR2_STR));

        this.inspected = new NetworkElementEntity();
        this.inspected.setElementId("iNsPeCtEdPoRt");
        this.inspected.setMacAddresses(asList(INSPMAC1_STR));

        this.ppg = new PortPairGroupEntity();

        this.inspectionPort = new InspectionPortEntity();
        this.inspectionPort.setIngressPort(this.ingress);
        this.inspectionPort.setEgressPort(this.egress);

        this.sfc = new ServiceFunctionChainEntity();

        this.inspectionHook = new InspectionHookEntity(this.inspected, this.sfc);
    }

    @After
    public void tearDown() throws Exception {

        if (this.redirApi != null) {
            this.redirApi.close();
        }
        File dbfile = new File(TEST_DB_FILENAME + ".h2.db");

        if (!dbfile.delete()) {
            throw new IllegalStateException("Failed to delete database file : " + dbfile.getAbsolutePath());
        }

        File tracefile = new File(TEST_DB_FILENAME + ".trace.db");
        if (tracefile.exists() && !tracefile.delete()) {
            throw new IllegalStateException("Failed to delete trace file : " + tracefile.getAbsolutePath());
        }
    }

    @Test
    public void testDb_PersistInspectionPort_verifyCorrectNumberOfMacsAdPortIps() throws Exception {

        persistInspectionPort();

        InspectionPortEntity tmp = this.txControl.requiresNew(() -> {
            return this.em.find(InspectionPortEntity.class, this.inspectionPort.getElementId());
        });

        assertEquals(2, tmp.getEgressPort().getMacAddresses().size());
        assertEquals(2, tmp.getEgressPort().getPortIPs().size());
        assertEquals(2, tmp.getIngressPort().getMacAddresses().size());
        assertEquals(2, tmp.getIngressPort().getPortIPs().size());
    }

    @Test
    public void testUtilsInspectionPortByNetworkElements() throws Exception {

        persistInspectionPort();

        RedirectionApiUtils utils = new RedirectionApiUtils(this.em, this.txControl);

        InspectionPortEntity foundPort = utils.findInspectionPortByNetworkElements(this.ingress, this.egress);

        assertNotNull(foundPort);
        assertEquals(this.inspectionPort.getElementId(), foundPort.getElementId());
    }

    @Test
    public void testUtilsInspHookByInspectedAndPort() throws Exception {
        persistInspectionHook();

        RedirectionApiUtils utils = new RedirectionApiUtils(this.em, this.txControl);

        InspectionHookEntity foundIH = this.txControl.required(() -> {
            ServiceFunctionChainEntity tmpSfc = this.em.find(ServiceFunctionChainEntity.class,
                    this.sfc.getElementId());

            InspectionHookEntity ihe = utils.findInspHookByInspectedAndPort(this.inspected, tmpSfc);

            assertNotNull(ihe);
            assertEquals(this.inspectionHook.getHookId(), ihe.getHookId());
            return ihe;
        });

        assertEquals(foundIH.getHookId(), this.inspectionHook.getHookId());
        assertEquals(foundIH.getServiceFunctionChain().getElementId(), this.sfc.getElementId());
        assertEquals(foundIH.getInspectedPort().getElementId(), this.inspected.getElementId());

    }

    @Test
    public void testUtilsRemoveSingleInspectionHook() throws Exception {
        persistInspectionHook();

        RedirectionApiUtils utils = new RedirectionApiUtils(this.em, this.txControl);

        utils.removeSingleInspectionHook(this.inspectionHook.getHookId());

        InspectionHookEntity inspectionHookEntity = this.txControl.required(() -> {
            InspectionHookEntity tmpInspectionHook = this.em.find(InspectionHookEntity.class, this.inspectionHook.getHookId());
            return tmpInspectionHook;
        });

        assertNull(inspectionHookEntity);
    }

    // Inspection port tests

    @Test
    public void testApiRegisterInspectionPort() throws Exception {
        this.redirApi = new NeutronSfcSdnRedirectionApi(this.txControl, this.em);

        InspectionPortElement inspectionPortElement = new InspectionPortEntity(null, null, this.ingress, this.egress);
        inspectionPortElement = (InspectionPortElement) this.redirApi.registerInspectionPort(inspectionPortElement);

        // Here we are mostly afraid of LazyInitializationException
        assertNotNull(inspectionPortElement.getElementId());
        assertNotNull(inspectionPortElement.getParentId());
        assertNotNull(inspectionPortElement.getIngressPort());
        assertNotNull(inspectionPortElement.getEgressPort());
        assertNotNull(inspectionPortElement.getEgressPort().getMacAddresses());
        assertNotNull(inspectionPortElement.getEgressPort().getElementId());
        assertNotNull(inspectionPortElement.getIngressPort());
        assertNotNull(inspectionPortElement.getIngressPort().getMacAddresses());
        assertNotNull(inspectionPortElement.getIngressPort().getElementId());
        inspectionPortElement.getIngressPort().getParentId();
        inspectionPortElement.getEgressPort().getParentId();

        final InspectionPortElement inspectionPortElementTmp = inspectionPortElement;
        NetworkElementEntity foundIngress = this.txControl.required(() -> {
            return this.em.find(NetworkElementEntity.class, inspectionPortElementTmp.getIngressPort().getElementId());
        });

        assertNotNull(foundIngress);
        assertEquals(inspectionPortElement.getIngressPort().getElementId(), foundIngress.getElementId());

        // Here we are afraid of lazyInitializationException
        foundIngress.getMacAddresses();
        foundIngress.getPortIPs();
        foundIngress.getElementId();
        foundIngress.getParentId();

        InspectionPortElement foundInspPortElement = this.redirApi.getInspectionPort(inspectionPortElement);
        assertEquals(inspectionPortElement.getIngressPort().getElementId(),
                foundInspPortElement.getIngressPort().getElementId());
        assertEquals(inspectionPortElement.getEgressPort().getElementId(),
                foundInspPortElement.getEgressPort().getElementId());
        assertEquals(inspectionPortElement.getElementId(), foundInspPortElement.getElementId());

        assertEquals(foundInspPortElement.getParentId(), inspectionPortElement.getParentId());
    }

    @Test
    public void testApiRegisterInspectionPortWithNetworkElementsAlreadyPersisted() throws Exception {
        this.redirApi = new NeutronSfcSdnRedirectionApi(this.txControl, this.em);

        this.txControl.required(() -> {
            this.em.persist(this.ingress);
            this.em.persist(this.egress);
            return null;
        });

        InspectionPortElement inspectionPortElement = new InspectionPortEntity(null, null, this.ingress, this.egress);

        // ... and the test
        inspectionPortElement = (InspectionPortElement) this.redirApi.registerInspectionPort(inspectionPortElement);
        assertNotNull(inspectionPortElement);
        assertNotNull(inspectionPortElement.getElementId());
        assertNotNull(inspectionPortElement.getParentId());
        assertNotNull(inspectionPortElement.getIngressPort());
        assertNotNull(inspectionPortElement.getEgressPort());
        assertEquals(this.ingress.getElementId(), inspectionPortElement.getIngressPort().getElementId());
        assertEquals(this.egress.getElementId(), inspectionPortElement.getEgressPort().getElementId());
    }

    @Test
    public void testApiRegisterInspectionPortWithParentId() throws Exception {
        this.redirApi = new NeutronSfcSdnRedirectionApi(this.txControl, this.em);

        InspectionPortElement inspectionPortElement = new InspectionPortEntity(null, null, this.ingress, this.egress);
        Element result = this.redirApi.registerInspectionPort(inspectionPortElement);

        assertNotNull(result.getParentId());
        String portGroupId = result.getParentId();

        RedirectionApiUtils utils = new RedirectionApiUtils(this.em, this.txControl);

        PortPairGroupEntity ppg = utils.findByPortPairgroupId(portGroupId);
        InspectionPortElement inspectionPortElement2 = new InspectionPortEntity(null, ppg,
                new NetworkElementEntity("IngressFoo", asList("IngressMac"), asList("IngressIP"), null),
                new NetworkElementEntity("EgressFoo", asList("EgressMac"), asList("EgressIP"), null));

        Element result2 = this.redirApi.registerInspectionPort(inspectionPortElement2);

        assertEquals(portGroupId, result2.getParentId());
    }

    @Test
    public void testApiRegisterInspectionPortWithInvalidParentId() throws Exception {
        this.exception.expect(IllegalArgumentException.class);

        this.redirApi = new NeutronSfcSdnRedirectionApi(this.txControl, this.em);

        PortPairGroupEntity ppg = new PortPairGroupEntity();
        ppg.setElementId("fooportgroup");

        InspectionPortElement inspectionPortElement = new InspectionPortEntity(null, ppg, this.ingress,
                this.egress);
        this.redirApi.registerInspectionPort(inspectionPortElement);
    }

    @Test
    public void testApi_RemoveSingleInspectionPort_VerifyPPGDeleted() throws Exception {
        this.redirApi = new NeutronSfcSdnRedirectionApi(this.txControl, this.em);

        InspectionPortEntity inspectionPortElement = new InspectionPortEntity(null, null, this.ingress, this.egress);

        Element registeredElement = this.redirApi.registerInspectionPort(inspectionPortElement);

        assertTrue(registeredElement instanceof InspectionPortEntity);
        String elementId = registeredElement.getElementId();

        InspectionPortEntity foundInspectionPort = this.txControl.required(() -> {
            InspectionPortEntity tmpInspectionPort = this.em.find(InspectionPortEntity.class, elementId);
            assertNotNull(tmpInspectionPort);
            return tmpInspectionPort;
        });

        assertEquals(elementId, foundInspectionPort.getElementId());
        String ppgId = foundInspectionPort.getParentId();
        String ingressPortId = foundInspectionPort.getIngressPort().getElementId();
        String egressPortId = foundInspectionPort.getEgressPort().getElementId();

        this.redirApi.removeInspectionPort(inspectionPortElement);

        this.txControl.required(() -> {

            assertNull(this.em.find(InspectionPortEntity.class, elementId));
            assertNull(this.em.find(PortPairGroupEntity.class, ppgId));
            assertNull(this.em.find(NetworkElementEntity.class, ingressPortId));
            assertNull(this.em.find(NetworkElementEntity.class, egressPortId));

            return null;
        });

    }

    @Test
    public void testApi_RemoveSingleInspectionPort_VerifyPPGNotDeleted() throws Exception {
        this.redirApi = new NeutronSfcSdnRedirectionApi(this.txControl, this.em);

        InspectionPortEntity inspectionPortElement = new InspectionPortEntity(null, null, this.ingress, this.egress);

        Element registeredElement = this.redirApi.registerInspectionPort(inspectionPortElement);

        assertTrue(registeredElement instanceof InspectionPortEntity);

        String elementId = registeredElement.getElementId();

        InspectionPortEntity foundInspectionPort = this.txControl.required(() -> {
            InspectionPortEntity tmpInspectionPort = this.em.find(InspectionPortEntity.class, elementId);
            assertNotNull(tmpInspectionPort);
            return tmpInspectionPort;
        });

        assertEquals(elementId, foundInspectionPort.getElementId());

        InspectionPortElement inspectionPortElement2 = new InspectionPortEntity(null,
                foundInspectionPort.getPortPairGroup(),
                new NetworkElementEntity("IngressFoo", asList("IngressMac"), asList("IngressIP"), null),
                new NetworkElementEntity("EgressFoo", asList("EgressMac"), asList("EgressIP"), null));

        registeredElement = this.redirApi.registerInspectionPort(inspectionPortElement2);

        String ppgId = foundInspectionPort.getParentId();

        this.redirApi.removeInspectionPort(inspectionPortElement);

        foundInspectionPort = this.txControl.required(() -> {
            return this.em.find(InspectionPortEntity.class, elementId);
        });

        PortPairGroupEntity ppg = this.txControl.required(() -> {
            return this.em.find(PortPairGroupEntity.class, ppgId);
        });

        assertNull(foundInspectionPort);
        assertNotNull(ppg);
    }

    // Inspection hooks test

    @Test
    public void testApiInstallInspectionHook_VerifySucceeds() throws Exception {
        persistInspectionPort();
        this.txControl.required(() -> {

            this.sfc.getPortPairGroups().add(this.ppg);
            this.em.persist(this.sfc);

            return null;
        });

        this.redirApi = new NeutronSfcSdnRedirectionApi(this.txControl, this.em);

        final String hookId = this.redirApi.installInspectionHook(this.inspected, this.sfc, 0L, VLAN, 0L,
                NA);

        assertNotNull(hookId);

        InspectionHookElement inspectionHookElement = this.txControl.required(() -> {
            InspectionHookEntity tmp = this.em.find(InspectionHookEntity.class, hookId);
            assertNotNull(tmp);
            assertEquals(tmp.getServiceFunctionChain().getElementId(), this.sfc.getElementId());
            return tmp;
        });

        // Here we are mostly afraid of LazyInitializationException
        assertNotNull(inspectionHookElement);
        assertNotNull(inspectionHookElement.getHookId());
        assertEquals(inspectionHookElement.getInspectedPort().getElementId(), this.inspected.getElementId());
    }

    @Test
    public void testApiInstallInspectionHook_WithNoInspectedPort_VerifyFails() throws Exception {

        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage("null passed for Inspection port !");

        this.redirApi = new NeutronSfcSdnRedirectionApi(this.txControl, this.em);

        this.redirApi.installInspectionHook(this.inspected, this.sfc, 0L, VLAN, 0L,
                NA);

        // Inspected port with non-existing id
        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage(StringStartsWith.startsWith("Cannot find type Service Function Chain"));

        this.redirApi.installInspectionHook(this.inspected, new ServiceFunctionChainEntity("foo"), 0L, VLAN, 0L,
                NA);
    }

    @Test
    public void testApiUpdateInspectionHook_WithExistingHook_VerifySucceeds() throws Exception {
        persistInspectionHook();

        String hookId = this.inspectionHook.getHookId();

        this.redirApi = new NeutronSfcSdnRedirectionApi(this.txControl, this.em);

        // Setup new SFC
        InspectionPortElement inspectionPort = new InspectionPortEntity(null,
                null,
                new NetworkElementEntity("IngressFoo", asList("IngressMac"), asList("IngressIP"), null),
                new NetworkElementEntity("EgressFoo", asList("EgressMac"), asList("EgressIP"), null));

        Element portPairEntity = this.redirApi.registerInspectionPort(inspectionPort);

        ServiceFunctionChainEntity newSfc = this.txControl.required(() -> {
            ServiceFunctionChainEntity tmpSfc = new ServiceFunctionChainEntity();
            tmpSfc.getPortPairGroups().add(new PortPairGroupEntity(portPairEntity.getParentId()));
            this.em.persist(tmpSfc);
            return tmpSfc;
        });

        InspectionHookEntity updatedHook = new InspectionHookEntity(this.inspected, newSfc);
        updatedHook.setHookId(hookId);

        // Act
        this.redirApi.updateInspectionHook(updatedHook);

        this.txControl.required(() -> {
            InspectionHookEntity tmp = this.em.find(InspectionHookEntity.class, hookId);
            assertNotNull(tmp);
            assertEquals(tmp.getServiceFunctionChain().getElementId(), newSfc.getElementId());
            return null;
        });
    }

    @Test
    public void testApiUpdateInspectionHook_WithMissingHook_VerifyFailure() throws Exception {
        persistInspectionPortAndSfc();

        this.redirApi = new NeutronSfcSdnRedirectionApi(this.txControl, this.em);

        InspectionHookEntity updatedHook = new InspectionHookEntity(this.inspected, this.sfc);
        updatedHook.setHookId("non-existing-id");

        this.exception.expect(IllegalArgumentException.class);
        this.exception.expectMessage(StringStartsWith.startsWith("Cannot find Inspection Hook"));

        // Act
        this.redirApi.updateInspectionHook(updatedHook);
    }

    @Test
    public void testApiRemoveInspectionHookById_InspectionHookDisappears() throws Exception {
        persistInspectionHook();

        InspectionHookEntity inspectionHookEntity = this.txControl.required(() -> {
            InspectionHookEntity tmpInspectionHook = this.em.find(InspectionHookEntity.class, this.inspectionHook.getHookId());
            return tmpInspectionHook;
        });

        assertNotNull(inspectionHookEntity);

        this.redirApi = new NeutronSfcSdnRedirectionApi(this.txControl, this.em);
        this.redirApi.removeInspectionHook(inspectionHookEntity.getHookId());

        inspectionHookEntity = this.txControl.required(() -> {
            InspectionHookEntity tmpInspectionHook = this.em.find(InspectionHookEntity.class, this.inspectionHook.getHookId());
            return tmpInspectionHook;
        });

        NetworkElementEntity inspectedPortNetworkElement = this.txControl.required(() -> {
            return this.em.find(NetworkElementEntity.class, this.inspected.getElementId());
        });

        assertNull(inspectionHookEntity);
        assertNull(inspectedPortNetworkElement);
    }

    @Test
    public void testApiGetInspectionHook() throws Exception {
        persistInspectionHook();

        InspectionHookElement inspectionHookElement = this.txControl.required(() -> {
            InspectionHookEntity tmp = this.em.find(InspectionHookEntity.class, this.inspectionHook.getHookId());
            assertNotNull(tmp);
            assertEquals(tmp.getServiceFunctionChain().getElementId(), this.sfc.getElementId());
            return tmp;
        });

        // Here we are mostly afraid of LazyInitializationException
        assertNotNull(inspectionHookElement);
        assertNotNull(inspectionHookElement.getHookId());
        assertEquals(inspectionHookElement.getInspectedPort().getElementId(), this.inspected.getElementId());
    }

    private InspectionPortEntity persistInspectionPort() {
        return this.txControl.required(() -> {
            this.em.persist(this.ppg);

            this.inspectionPort.setPortPairGroup(this.ppg);
            this.em.persist(this.inspectionPort);

            this.ppg.getPortPairs().add(this.inspectionPort);

            this.em.merge(this.ppg);
            return this.inspectionPort;
        });
    }

    private ServiceFunctionChainEntity persistInspectionPortAndSfc() {
        persistInspectionPort();
        return this.txControl.required(() -> {
            this.sfc.getPortPairGroups().add(this.ppg);
            this.em.persist(this.sfc);

            this.ppg.setServiceFunctionChain(this.sfc);
            this.em.merge(this.ppg);
            return this.sfc;
        });
    }

    private InspectionHookEntity persistInspectionHook() {
        persistInspectionPort();
        return this.txControl.required(() -> {
            this.sfc.getPortPairGroups().add(this.ppg);
            this.em.persist(this.sfc);

            this.ppg.setServiceFunctionChain(this.sfc);
            this.em.merge(this.ppg);

            this.em.persist(this.inspected);

            this.em.persist(this.inspectionHook);
            return this.inspectionHook;
        });
    }

}
