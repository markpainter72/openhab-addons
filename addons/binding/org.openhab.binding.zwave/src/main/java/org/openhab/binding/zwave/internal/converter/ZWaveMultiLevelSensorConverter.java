/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zwave.internal.converter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.zwave.handler.ZWaveThingHandler.ZWaveThingChannel;
import org.openhab.binding.zwave.internal.protocol.SerialMessage;
import org.openhab.binding.zwave.internal.protocol.ZWaveNode;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveCommandClass;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveMultiLevelSensorCommandClass;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveMultiLevelSensorCommandClass.SensorType;
import org.openhab.binding.zwave.internal.protocol.commandclass.ZWaveMultiLevelSensorCommandClass.ZWaveMultiLevelSensorValueEvent;
import org.openhab.binding.zwave.internal.protocol.event.ZWaveCommandClassValueEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/***
 * ZWaveMultiLevelSensorConverter class. Converter for communication with the {@link ZWaveMultiLevelSensorCommandClass}.
 * Implements polling of the sensor status and receiving of sensor events.
 *
 * @author Chris Jackson
 * @author Jan-Willem Spuij
 */
public class ZWaveMultiLevelSensorConverter extends ZWaveCommandClassConverter {

    private static final Logger logger = LoggerFactory.getLogger(ZWaveMultiLevelSensorConverter.class);

    /**
     * Constructor. Creates a new instance of the {@link ZWaveMultiLevelSensorConverter} class.
     *
     */
    public ZWaveMultiLevelSensorConverter() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SerialMessage> executeRefresh(ZWaveThingChannel channel, ZWaveNode node) {
        ZWaveMultiLevelSensorCommandClass commandClass = (ZWaveMultiLevelSensorCommandClass) node
                .resolveCommandClass(ZWaveCommandClass.CommandClass.SENSOR_MULTILEVEL, channel.getEndpoint());
        if (commandClass == null) {
            return null;
        }

        logger.debug("NODE {}: Generating poll message for {}, endpoint {}", node.getNodeId(),
                commandClass.getCommandClass().getLabel(), channel.getEndpoint());
        SerialMessage serialMessage = node.encapsulate(commandClass.getValueMessage(), commandClass,
                channel.getEndpoint());
        List<SerialMessage> response = new ArrayList<SerialMessage>(1);
        response.add(serialMessage);
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public State handleEvent(ZWaveThingChannel channel, ZWaveCommandClassValueEvent event) {
        String sensorType = channel.getArguments().get("sensorType");
        String sensorScale = channel.getArguments().get("config_scale");
        ZWaveMultiLevelSensorValueEvent sensorEvent = (ZWaveMultiLevelSensorValueEvent) event;

        // Don't trigger event if this item is bound to another sensor type
        if (sensorType != null && SensorType.valueOf(sensorType) != sensorEvent.getSensorType()) {
            return null;
        }

        BigDecimal val = (BigDecimal) event.getValue();
        // Perform a scale conversion if needed
        if (sensorScale != null && Integer.parseInt(sensorScale) != sensorEvent.getSensorScale()) {
            int intType = Integer.parseInt(sensorType);
            SensorType senType = SensorType.getSensorType(intType);
            if (senType == null) {
                logger.error("NODE {}: Error parsing sensor type {}", event.getNodeId(), sensorType);
            } else {
                switch (senType) {
                    case TEMPERATURE:
                        val = convertTemperature(sensorEvent.getSensorScale(), Integer.parseInt(sensorScale), val);
                        // Perform a scale conversion if needed
                        if (sensorScale != null && Integer.parseInt(sensorScale) != sensorEvent.getSensorScale()) {
                            val = convertTemperature(sensorEvent.getSensorScale(), Integer.parseInt(sensorScale), val);
                        }
                        break;
                    default:
                        break;
                }
            }

            logger.debug("NODE {}: Sensor is reporting scale {}, requiring conversion to {}. Value is now {}.",
                    event.getNodeId(), sensorEvent.getSensorScale(), sensorScale, val);
        }

        return new DecimalType(val);
    }
}
