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
package org.osc.controller.nsfc.exceptions;

import org.openstack4j.model.common.Resource;

public class SdnControllerResponseNsfcException extends RuntimeException {

    public enum Operation {Create, Update, Delete}

    private static final long serialVersionUID = -5757616005985528232L;

    protected final Class<? extends Resource> objectClass;
    protected final SdnControllerResponseNsfcException.Operation operation;

    public SdnControllerResponseNsfcException(SdnControllerResponseNsfcException.Operation operation, Class<? extends Resource> objectClass) {
        this.operation = operation;
        this.objectClass = objectClass;
    }

    public SdnControllerResponseNsfcException(SdnControllerResponseNsfcException.Operation operation, Class<? extends Resource> objectClass, Throwable cause) {
        super(cause);
        this.operation = operation;
        this.objectClass = objectClass;
    }

    @Override
    public String getMessage() {
        return String.format("%s %s failed! SDN Controller threw  %s: %s!", this.operation, this.objectClass.getSimpleName(),
                getCause().getClass().getSimpleName(), getCause().getMessage());
    }
}
