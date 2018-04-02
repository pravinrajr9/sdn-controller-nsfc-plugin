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

import static com.google.common.base.Preconditions.checkArgument;
import static org.osc.controller.nsfc.exceptions.SdnControllerResponseNsfcException.Operation.*;

import java.util.ArrayList;
import java.util.List;

import org.openstack4j.api.OSClient.OSClientV3;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.network.Port;
import org.openstack4j.model.network.ext.FlowClassifier;
import org.openstack4j.model.network.ext.PortChain;
import org.openstack4j.model.network.ext.PortPair;
import org.openstack4j.model.network.ext.PortPairGroup;
import org.osc.controller.nsfc.exceptions.SdnControllerResponseNsfcException;

public class OsCalls {

    private OSClientV3 osClient;

    public OsCalls(OSClientV3 osClient) {
        this.osClient = osClient;
    }

    public FlowClassifier createFlowClassifier(FlowClassifier flowClassifier) {
        checkArgument(flowClassifier != null, "null passed for %s !", "Flow Classifier");

        flowClassifier = flowClassifier.toBuilder().id(null).build();

        try {
            flowClassifier = this.osClient.sfc().flowclassifiers().create(flowClassifier);
            if (flowClassifier == null) {
                throw new RuntimeException("Create Flow Classifier operation returned null");
            }
        } catch (Exception e) {
            throw new SdnControllerResponseNsfcException(Create, FlowClassifier.class, e);
        }

        return flowClassifier;

    }

    public PortChain createPortChain(PortChain portChain) {
        checkArgument(portChain != null, "null passed for %s !", "Port Chain");
        portChain = portChain.toBuilder().id(null).build();

        try {
            portChain = this.osClient.sfc().portchains().create(portChain);
            if (portChain == null) {
                throw new RuntimeException("Create Port Chain operation returned null");
            }
        } catch (Exception e) {
            throw new SdnControllerResponseNsfcException(Create, PortChain.class, e);
        }

        return initializePortChainCollections(portChain);
    }

    public PortPairGroup createPortPairGroup(PortPairGroup portPairGroup) {
        checkArgument(portPairGroup != null, "null passed for %s !", "Port Pair Group");
        portPairGroup = portPairGroup.toBuilder().id(null).build();

        try {
            portPairGroup = this.osClient.sfc().portpairgroups().create(portPairGroup);
            if (portPairGroup == null) {
                throw new RuntimeException("Create Port Pair Group operation returned null");
            }
        } catch (Exception e) {
            throw new SdnControllerResponseNsfcException(Create, PortPairGroup.class, e);
        }

        return portPairGroup;
    }

    public PortPair createPortPair(PortPair portPair) {
        checkArgument(portPair != null, "null passed for %s !", "Port Pair");
        portPair = portPair.toBuilder().id(null).build();

        try {
            portPair = this.osClient.sfc().portpairs().create(portPair);
            if (portPair == null) {
                throw new RuntimeException("Create Port Pair operation returned null");
            }
        } catch (Exception e) {
            throw new SdnControllerResponseNsfcException(Create, PortPair.class, e);
        }

        return portPair;
    }

    public List<? extends PortPairGroup> listPortPairGroups() {
        return this.osClient.sfc().portpairgroups().list();
    }

    public List<? extends PortPair> listPortPairs() {
        return this.osClient.sfc().portpairs().list();
    }

    public List<? extends PortChain> listPortChains() {
        return this.osClient.sfc().portchains().list();
    }

    public FlowClassifier getFlowClassifier(String flowClassifierId) {
        return this.osClient.sfc().flowclassifiers().get(flowClassifierId);
    }

    public PortChain getPortChain(String portChainId) {
        PortChain portChain = this.osClient.sfc().portchains().get(portChainId);
        return initializePortChainCollections(portChain);
    }

    public PortPairGroup getPortPairGroup(String portPairGroupId) {
        return this.osClient.sfc().portpairgroups().get(portPairGroupId);
    }

    public PortPair getPortPair(String portPairId) {
        return this.osClient.sfc().portpairs().get(portPairId);
    }

    public Port getPort(String portId) {
        return this.osClient.networking().port().get(portId);
    }

    public PortChain updatePortChain(String portChainId, PortChain portChain) {
        checkArgument(portChainId != null, "null passed for %s !", "Port Chain Id");
        checkArgument(portChain != null, "null passed for %s !", "Port Chain");

        // OS won't let us modify some attributes. Must be null on update object
        portChain = portChain.toBuilder().id(null).projectId(null).chainParameters(null).chainId(null).build();

        try {
            portChain = this.osClient.sfc().portchains().update(portChainId, portChain);
            if (portChain == null) {
                throw new RuntimeException("Update Port Chain operation returned null for port chain " + portChainId);
            }
        } catch (Exception e) {
            throw new SdnControllerResponseNsfcException(Update, PortChain.class, e);
        }

        return initializePortChainCollections(portChain);
    }

    public PortPairGroup updatePortPairGroup(String portPairGroupId, PortPairGroup portPairGroup) {
        checkArgument(portPairGroupId != null, "null passed for %s !", "Port Pair Group Id");
        checkArgument(portPairGroup != null, "null passed for %s !", "Port Pair Group");

        // OS won't let us modify some attributes. Must be null on update object
        portPairGroup  = portPairGroup.toBuilder().id(null).projectId(null).portPairGroupParameters(null).build();

        try {
            portPairGroup = this.osClient.sfc().portpairgroups().update(portPairGroupId, portPairGroup);
            if (portPairGroup == null) {
                throw new RuntimeException("Update Port Pair Group operation returned null for port pair" + portPairGroupId);
            }
        } catch (Exception e) {
            throw new SdnControllerResponseNsfcException(Update, PortPairGroup.class, e);
        }
        return portPairGroup;
    }

    public void deleteFlowClassifier(String flowClassifierId) {
        try {
            ActionResponse response = this.osClient.sfc().flowclassifiers().delete(flowClassifierId);
            if (!response.isSuccess()) {
                if (response.getCode() == 404) {
                    return;
                }
                String msg = String.format("Deleting flow classifier %s Response %d %s", flowClassifierId, response.getCode(), response.getFault());
                throw new RuntimeException(msg);
            }
        } catch (Exception e) {
            throw new SdnControllerResponseNsfcException(Delete, PortPair.class, e);
        }
    }

    public void deletePortChain(String portChainId) {
        try {
            ActionResponse response = this.osClient.sfc().portchains().delete(portChainId);
            if (!response.isSuccess()) {
                if (response.getCode() == 404) {
                    return;
                }
                String msg = String.format("Deleting port chain %s Response %d %s", portChainId, response.getCode(), response.getFault());
                throw new RuntimeException(msg);
            }
        } catch (Exception e) {
            throw new SdnControllerResponseNsfcException(Delete, PortPair.class, e);
        }
    }

    public void deletePortPairGroup(String portPairGroupId) {
        try {
            ActionResponse response = this.osClient.sfc().portpairgroups().delete(portPairGroupId);
            if (!response.isSuccess()) {
                if (response.getCode() == 404) {
                    return;
                }
                String msg = String.format("Deleting port pair %s Response %d %s", portPairGroupId, response.getCode(), response.getFault());
                throw new RuntimeException(msg);
            }
        } catch (Exception e) {
            throw new SdnControllerResponseNsfcException(Delete, PortPair.class, e);
        }
    }

    public void deletePortPair(String portPairId) {
        try {
            ActionResponse response = this.osClient.sfc().portpairs().delete(portPairId);
            if (!response.isSuccess()) {
                if (response.getCode() == 404) {
                    return;
                }
                String msg = String.format("Deleting port pair %s Response %d %s", portPairId, response.getCode(), response.getFault());
                throw new RuntimeException(msg);
            }
        } catch (Exception e) {
            throw new SdnControllerResponseNsfcException(Delete, PortPair.class, e);
        }
    }

    private PortChain initializePortChainCollections(PortChain portChain) {
        if (portChain == null) {
            return null;
        }

        if (portChain.getFlowClassifiers() == null) {
            portChain = portChain.toBuilder().flowClassifiers(new ArrayList<>()).build();
        }

        if (portChain.getPortPairGroups() == null) {
            portChain = portChain.toBuilder().portPairGroups(new ArrayList<>()).build();
        }

        return portChain;
    }
}
