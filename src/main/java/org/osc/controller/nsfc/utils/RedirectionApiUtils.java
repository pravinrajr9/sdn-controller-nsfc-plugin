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
package org.osc.controller.nsfc.utils;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.osc.controller.nsfc.entities.InspectionHookEntity;
import org.osc.controller.nsfc.entities.InspectionPortEntity;
import org.osc.controller.nsfc.entities.NetworkElementEntity;
import org.osc.controller.nsfc.entities.PortPairGroupEntity;
import org.osc.controller.nsfc.entities.ServiceFunctionChainEntity;
import org.osc.sdk.controller.element.Element;
import org.osc.sdk.controller.element.InspectionPortElement;
import org.osc.sdk.controller.element.NetworkElement;
import org.osgi.service.transaction.control.TransactionControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedirectionApiUtils {

    private static final Logger LOG = LoggerFactory.getLogger(RedirectionApiUtils.class);

    private TransactionControl txControl;
    private EntityManager em;

    public RedirectionApiUtils(EntityManager em, TransactionControl txControl) {
        this.em = em;
        this.txControl = txControl;
    }

    private NetworkElementEntity makeNetworkElementEntity(NetworkElement networkElement) {
        NetworkElementEntity retVal = new NetworkElementEntity();

        retVal.setElementId(networkElement.getElementId());
        retVal.setMacAddresses(networkElement.getMacAddresses());
        retVal.setPortIPs(networkElement.getPortIPs());

        return retVal;
    }

    public InspectionPortEntity makeInspectionPortEntity(InspectionPortElement inspectionPortElement) {
        throwExceptionIfNullElement(inspectionPortElement, "Inspection Port");

        NetworkElement ingress = inspectionPortElement.getIngressPort();
        throwExceptionIfNullElement(ingress, "ingress element.");
        NetworkElementEntity ingressEntity = makeNetworkElementEntity(ingress);

        NetworkElement egress = inspectionPortElement.getEgressPort();
        NetworkElementEntity egressEntity = null;
        throwExceptionIfNullElement(egress, "egress element.");

        if (ingressEntity != null && ingressEntity.getElementId().equals(egress.getElementId())) {
            egressEntity = ingressEntity;
        } else {
            egressEntity = makeNetworkElementEntity(egress);
        }
        String ppgId = inspectionPortElement.getParentId();
        PortPairGroupEntity ppg = ppgId == null ? null : this.em.find(PortPairGroupEntity.class, ppgId);

        return new InspectionPortEntity(inspectionPortElement.getElementId(), ppg, ingressEntity, egressEntity);
    }

    public InspectionHookEntity makeInspectionHookEntity(NetworkElement inspectedPort,
            NetworkElement sfcNetworkElement) {

        throwExceptionIfNullElement(inspectedPort, "inspected port!");

        ServiceFunctionChainEntity sfc = findBySfcId(sfcNetworkElement.getElementId());

        NetworkElementEntity inspected = makeNetworkElementEntity(inspectedPort);
        InspectionHookEntity retVal = new InspectionHookEntity(inspected, sfc);

        inspected.setInspectionHook(retVal);

        return retVal;
    }

    public PortPairGroupEntity findByPortPairgroupId(String ppgId) {

        return this.txControl.required(() -> {
            return this.em.find(PortPairGroupEntity.class, ppgId);
        });
    }

    public void removePortPairGroup(String ppgId) {

        this.txControl.required(() -> {
            PortPairGroupEntity ppg = this.em.find(PortPairGroupEntity.class, ppgId);
            this.em.remove(ppg);
            return null;
        });
    }

    public ServiceFunctionChainEntity findBySfcId(String sfcId) {

        return this.txControl.required(() -> {
            return this.em.find(ServiceFunctionChainEntity.class, sfcId);
        });
    }

    public InspectionPortEntity findInspectionPortByNetworkElements(NetworkElement ingress, NetworkElement egress) {
        return this.txControl.required(() -> {

        String ingressId = ingress != null ? ingress.getElementId() : null;
        String egressId = ingress != null ? egress.getElementId() : null;

        CriteriaBuilder cb = this.em.getCriteriaBuilder();
        CriteriaQuery<InspectionPortEntity> criteria = cb.createQuery(InspectionPortEntity.class);
        Root<InspectionPortEntity> root = criteria.from(InspectionPortEntity.class);
            criteria.select(root).where(cb.and(cb.equal(root.join("ingressPort").get("elementId"), ingressId),
                cb.equal(root.join("egressPort").get("elementId"), egressId)));
        Query q= this.em.createQuery(criteria);

        try {
            @SuppressWarnings("unchecked")
            List<InspectionPortEntity> ports = q.getResultList();
            if (ports == null || ports.size() == 0) {
                LOG.warn(String.format("No Inspection Ports by ingress %s and egress %s", ingressId, egressId));
                return null;
            } else if (ports.size() > 1) {
                LOG.warn(String.format("Multiple results! Inspection Ports by ingress %s and egress %s", ingressId,
                        egressId));
            }
            return ports.get(0);

        } catch (Exception e) {
            LOG.error(String.format("Finding Inspection Ports by ingress %s and egress %s", ingress.getElementId(),
                    egress.getElementId()), e);
            return null;
        }
        });
    }

    public InspectionPortEntity txInspectionPortEntityById(String id) {
        return this.em.find(InspectionPortEntity.class, id);
    }

    public void removeSingleInspectionHook(String hookId) {
        if (hookId == null) {
            LOG.warn("Attempt to remove Inspection Hook with null id");
            return;
        }

        this.txControl.required(() -> {
            InspectionHookEntity dbInspectionHook = this.em.find(InspectionHookEntity.class, hookId);

            if (dbInspectionHook == null) {
                LOG.warn("Attempt to remove nonexistent Inspection Hook for id " + hookId);
                return null;
            }
            NetworkElementEntity dbInspectedPort = dbInspectionHook.getInspectedPort();
            ServiceFunctionChainEntity sfc = dbInspectionHook.getServiceFunctionChain();
            sfc.getInspectionHooks().remove(dbInspectionHook);

            this.em.remove(dbInspectionHook);
            this.em.remove(dbInspectedPort);
            this.em.merge(sfc);
            return null;
        });
    }

    public void removeSingleInspectionPort(String inspectionPortId) {
        this.txControl.required(() -> {
            InspectionPortEntity inspectionPort = this.em.find(InspectionPortEntity.class, inspectionPortId);
            this.em.remove(inspectionPort);
            return null;
        });
    }

    /**
     * Assumes arguments are not null
     */
    public InspectionHookEntity findInspHookByInspectedAndPort(NetworkElement inspected,
            ServiceFunctionChainEntity inspectionSfc) {
        LOG.info(String.format("Finding Inspection hooks by inspected %s and sfc %s", inspected,
                inspectionSfc.getElementId()));

        return this.txControl.required(() -> {

            String inspectedId = inspected.getElementId();
            ServiceFunctionChainEntity sfc = this.em.find(ServiceFunctionChainEntity.class,
                    inspectionSfc.getElementId());

            CriteriaBuilder cb = this.em.getCriteriaBuilder();
            CriteriaQuery<InspectionHookEntity> criteria = cb.createQuery(InspectionHookEntity.class);
            Root<InspectionHookEntity> root = criteria.from(InspectionHookEntity.class);
            criteria.select(root).where(cb.and(cb.equal(root.join("inspectedPort").get("elementId"), inspectedId),
                    cb.equal(root.join("serviceFunctionChain"), sfc)));
            Query q = this.em.createQuery(criteria);

            @SuppressWarnings("unchecked")
            List<InspectionHookEntity> inspectionHooks = q.getResultList();
            if (inspectionHooks == null || inspectionHooks.size() == 0) {
                LOG.warn(String.format("No Inspection hooks by inspected %s and sfc %s", inspectedId, sfc));
                return null;
            } else if (inspectionHooks.size() > 1) {
                String msg = String.format("Multiple results! Inspection hooks by inspected %s and sfc %s", inspectedId,
                            sfc);
                LOG.warn(msg);
                throw new IllegalStateException(msg);
            }
            return inspectionHooks.get(0);
        });
    }

    /**
     * Throw exception message in the format "null passed for 'type'!"
     */
    public void throwExceptionIfNullElement(Element element, String type) {
        if (element == null) {
            String msg = String.format("null passed for %s !", type);
            LOG.error(msg);
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * Throw exception message in the format "null passed for 'type'!"
     */
    public void throwExceptionIfNullElementAndId(Element element, String type) {
        if (element == null || element.getElementId() == null) {
            String msg = String.format("null passed for %s !", type);
            LOG.error(msg);
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * Throw exception message in the format "Cannot find type by id: id!"
     */
    public void throwExceptionIfCannotFindById(Object element, String type, String id) {
        if (element == null) {
            String msg = String.format("Cannot find %s by id: %s!", type, id);
            LOG.error(msg);
            throw new IllegalArgumentException(msg);
        }
    }
}
