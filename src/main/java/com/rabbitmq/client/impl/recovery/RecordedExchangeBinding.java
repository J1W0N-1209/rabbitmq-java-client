// Copyright (c) 2007-2025 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

package com.rabbitmq.client.impl.recovery;

import java.io.IOException;

/**
 * @since 3.3.0
 */
public class RecordedExchangeBinding extends RecordedBinding {
    public RecordedExchangeBinding(AutorecoveringChannel channel) {
        super(channel);
    }

    @Override
    public void recover() throws IOException {
        this.channel.getDelegate().exchangeBind(this.destination, this.source, this.routingKey, this.arguments);
    }
    
    @Override
    public String toString() {
        return "RecordedExchangeBinding[source=" + source + ", destination=" + destination + ", routingKey=" + routingKey + ", arguments=" + arguments + ", channel=" + channel + "]";
    }
}
