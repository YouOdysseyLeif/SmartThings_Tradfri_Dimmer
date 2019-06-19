/**
 *  Copyright 2018 K Andrews
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *  
 *  Version Author		Note
 *  1.0		K Andrews	Initial release
 *  1.1		K Andrews	Added Configure button
 *  1.2		L Gilbertson	Changed to attempt to control Tradfri On Off Button
 *
 *	This device handler works with the IKEA TRÅDFRI On/Off swtich
 *	A slow turn increased or decreased the level gradually
 *	A fast turn increases to 100% or decreases to 0% immediately
 *  
 *	Add this device handler to SmartThings through the "My Device Handlers" page of the IDE
 *	Click the "Create New Device Handler" button, select "From Code"
 *	then paste all the contents of this file.
 *
 *	To add the device to SmartThings press the pairing button 4 times within 5 seconds then start
 *	a search in the SmarthThings mobile app.  The divice should appear as "IKEA TRÅDFRI On/Off Button"
 *
 */

import physicalgraph.zigbee.zcl.DataType

metadata {
    definition (name: "IKEA Button", namespace: "youodysseyleif", author: "Leif Gilbertson") {
        capability "Switch"
        capability "Sensor"
        capability "Switch Level"
        capability "Configuration"
		capability "Refresh"
		capability "Button"
		capability "Battery"

        //fingerprint profileId: "0104", inClusters: "0000, 0001, 0003, 0009, 0B05, 1000", outClusters: "0003, 0004, 0006, 0008, 0019, 1000", manufacturer: "IKEA of Sweden",model: "TRADFRI wireless dimmer",deviceJoinName: "IKEA Tradfri Dimmer"
        fingerprint profileId: "C05E", inClusters: "0000, 0001, 0003, 0009, 0B05, 1000", outClusters: "0003, 0004, 0006, 0008, 0019, 1000", manufacturer: "IKEA of Sweden",model: "TRADFRI wireless dimmer",deviceJoinName: "IKEA Tradfri Dimmer"
	}

    tiles(scale: 2) {
        // TODO: define your main and details tiles here
        multiAttributeTile(name:"lastAction", type: "generic", width: 6, height: 4){
            tileAttribute ("device.battery", key: "SECONDARY_CONTROL") {
                attributeState "battery", label:'${currentValue}% battery',icon:"st.Outdoor.outdoor3", unit:"", backgroundColors:[
                [value: 30, color: "#ff0000"],
                [value: 40, color: "#760000"],
                [value: 60, color: "#ff9900"],
                [value: 80, color: "#007600"]
                ]
            }
            tileAttribute ("device.lastAction", key: "PRIMARY_CONTROL") {
                attributeState "active", label:'${currentValue}', icon:"st.Home.home30"
            }

        }
        //        valueTile("lastAction", "device.lastAction", width: 6, height: 2) {
        //			state("lastAction", label:'${currentValue}')
        //		}

        valueTile("battery2", "device.battery", decoration: "flat", inactiveLabel: false, width: 5, height: 1) {
            state("battery", label:'${currentValue}% battery', unit:"")
        }
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 1, height: 1) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        //        standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
        //            state "default", label:"bind", action:"configure"
        //        }
    	main "lastAction"
    	details(["lastAction","battery2","refresh","configure"])
    }


}

// Parse incoming device messages to generate events
def parse(String description) {
	log.trace "ZigBee DTH - Executing parse() for device ${device.displayName}"
	
	def iValue = device.currentValue("level")
	def result = null
	def descMap = zigbee.parseDescriptionAsMap(description)

	if (descMap && descMap.clusterInt == 0x0008) {
		switch (descMap.commandInt) {
		case 0x01:
			// Record start time and direction of turn
			state.start = now()
			state.direction = Integer.parseInt(descMap.data[0], 8)
			break
		case 0x04:
			if (descMap.data[0] == "00") {
				log.debug "Fast turn acw, value to 0"
				result = createEvent(name: "level", value: 0)
			} else {
				log.debug "Fast turn cw, value to 100"
				result = createEvent(name: "level", value: 100)
			}
			break
		case 0x05:
			// Record start time and direction of turn
			state.start = now()
			state.direction = Integer.parseInt(descMap.data[0], 8)
			break
		case 0x07:
			// Stop turning
			// Calculate change in level based on direction and time
			long iTime = now() - state.start

			log.debug "Stop turning after $iTime ms, direction: $state.direction"

			def iChange = 0
			Integer iNewLevel = 0

			// Ignore turns over 3 seconds, probably a lag issue
			if (iTime > 5000) {
				iTime = 0
			}

			// Change based on 3 seconds for full 0-100 change in brightness
			iChange = iTime/3000 * 100
            
            switch (state.direction) {
			case 00:
				iNewLevel = device.currentValue("level") + iChange
				if (iNewLevel > 100) {
					iNewLevel = 100
				}
				break
			case 01:
				iNewLevel = device.currentValue("level") - iChange
				if (iNewLevel < 0) {
					iNewLevel = 0
				}
				break
			}

			result = createEvent(name: "level", value: iNewLevel)

			log.debug "Changing to level $iNewLevel"
			break
		default:
			log.debug "MAP ${descMap}"
			break
		}
	} else {
		log.warn "DID NOT PARSE MESSAGE for description : $description"
		log.debug "MAP ${descMap}"
	}
	return result
}

def off() {
	log.trace "ZigBee DTH - Executing off() for device ${device.displayName}"
}

def on() {
	log.trace "ZigBee DTH - Executing on() for device ${device.displayName}"
}

def setLevel(value) {
	log.trace "ZigBee DTH - Executing setLevel($value) for device ${device.displayName}"
	sendEvent(name:"level", value:value)
}

def ping() {
	log.trace "ZigBee DTH - Executing ping() for device ${device.displayName}"
	refresh()
}

def refresh() {
	log.trace "ZigBee DTH - Executing refresh() for device ${device.displayName}"
}

def installed() {
	log.trace "ZigBee DTH - Executing installed() for device ${device.displayName}"

	// Set default values
	sendEvent(name:"level", value:0)
	state.start = now()
	state.direction = 0
}

def uninstalled() {
	log.trace "ZigBee DTH - Executing uninstalled() for device ${device.displayName}"
}

def configure() {
	log.trace "ZigBee DTH - Executing configure() for device ${device.displayName}"
	zigbee.levelConfig()
}
