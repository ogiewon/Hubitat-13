/**
 *  Zooz Motion Sensor ZSE18 Advanced v1.0.1
 *  (Model: ZSE18)
 *
 *  Changelog:
 *
 *    1.0.1 (05/09/2020)
 *      - Added Import Url
 *
 *    1.0 (05/04/2018)
 *      - Initial Release
 *
 *
 *  Copyright 2020 Kevin LaFramboise
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
*/
import groovy.transform.Field

@Field static Map commandClassVersions = [
	0x55: 1,	// TransportServices (2)
	0x59: 1,	// AssociationGrpInfo
	0x5A: 1,	// DeviceResetLocally
	0x5E: 2,	// ZwaveplusInfo
	0x6C: 1,	// Supervision
	0x70: 1,	// Configuration
	0x71: 3,	// Notification (5)
	0x72: 2,	// ManufacturerSpecific
	0x73: 1,	// Powerlevel
	0x7A: 2,	// FirmwareUpdateMd (3)
	0x80: 1,	// Battery
	0x84: 2,	// WakeUp
	0x85: 2,	// Association
	0x86: 1,	// Version (2)
	0x98: 1,	// Security
	0x9F: 1		// Security 2
]

metadata {
	definition (
		name: "Zooz Motion Sensor ZSE18 Advanced",
		namespace: "krlaframboise",
		author: "Kevin LaFramboise",
		importUrl: "https://raw.githubusercontent.com/krlaframboise/Hubitat/master/drivers/krlaframboise/zooz-motion-sensor-zse18-advanced.src/zooz-motion-sensor-zse18-advanced.groovy"
	) {
		capability "Sensor"
		capability "Motion Sensor"
		capability "Acceleration Sensor"
		capability "Battery"
		capability "Configuration"
		capability "Refresh"
		capability "Power Source"
		
		fingerprint deviceId:"0012", mfr:"027A", prod:"0301", inClusters:"0x5E,0x85,0x59,0x55,0x86,0x72,0x5A,0x73,0x80,0x98,0x9F,0x71,0x30,0x84,0x70,0x6C,0x7A", deviceJoinName: "Zooz Motion Sensor ZSE18 Advanced" // ZSE18(secure)		
		fingerprint deviceId:"0012", mfr:"027A", prod:"0301", inClusters:"0x5E,0x85,0x59,0x55,0x86,0x72,0x5A,0x73,0x80,0x9F,0x71,0x30,0x84,0x70,0x6C,0x7A", deviceJoinName: "Zooz Motion Sensor ZSE18 Advanced" // ZSE18		
		fingerprint deviceId:"0012", mfr:"027A", prod:"0301", inClusters:"0x5E,0x85,0x59,0x55,0x86,0x72,0x5A,0x73,0x98,0x9F,0x71,0x30,0x70,0x6C,0x7A", deviceJoinName: "Zooz Motion Sensor ZSE18 Advanced" // ZSE18 (included as USB device)
	}
	
	preferences {
		getOptionsInput(motionSensitivityParam)
		getOptionsInput(motionClearedDelayParam)
		getOptionsInput(shockAlarmParam)
		getOptionsInput(ledParam)
		getOptionsInput(basicSetValueParam)

		input "assocDNIs", "string",
			title: "<strong>Device Associations</strong>",
			description: "<p>Associations are an advance feature that allow you to establish direct communication between Z-Wave devices.  To make this motion sensor control another Z-Wave device, get that device's Device Network Id from the My Devices section of the IDE and enter the id below.  It supports up to 4 associations and you can use commas to separate the device network ids.</p><p><strong>WARNING: </strong>If you add a device's Device Network ID to the list below and then remove that device from SmartThings, you <strong>MUST</strong> come back and remove it from the list below.  Failing to do this will substantially increase the number of z-wave messages being sent by this device and could affect the stability of your z-wave mesh.</p><p><strong>Enter Device Network IDs for Association:</strong></p>"
			required: false

		// getOptionsInput(sendBasicSetParam)
		// getOptionsInput(sensorBinaryReportsParam)
		// getOptionsInput(lowBatteryAlarmParam)

		input "debugOutput", "bool",
			title: "Enable debug logging?",
			defaultValue: true,
			required: false
	}
}

private getOptionsInput(param) {
	input "configParam${param.num}", "enum",
		title: "${param.name}:",
		required: false,
		defaultValue: "${param.defaultValue}",
		options: param.options
}

private getAssocDNIsSetting() {
	def val = settings?.assocDNIs
	return ((val && (val.trim() != "0")) ? val : "") // new iOS app has no way of clearing string input so workaround is to have users enter 0.
}



def installed() {
	initializePowerSource()
}

def updated() {
	if (!isDuplicateCommand(state.lastUpdated, 5000)) {
		state.lastUpdated = new Date().time
		logTrace "updated()"
		
		if (device.currentValue("powerSource") == "dc") {
			return configure()
		}
		else {
			logForceWakeupMessage "Configuration changes will be sent to the device the next time it wakes up."
		}
	}
}

private initializePowerSource() {
	if (!device.currentValue("powerSource") || (device.currentValue("powerSource") == "unknown")) {
		if (getDataValue("inClusters")?.contains("0x80")) {
			val = "unknown"
		}
		else {
			val = "dc"
			sendEvent(getEventMap("battery", 100, "%"))
		}
		sendEvent(getEventMap("powerSource", val))
	}
}


def configure() {
	logTrace "configure()"
	def cmds = []

	if (!state.isConfigured) {
		logTrace "Waiting 2 second because this is the first time being configured"
		cmds << "delay 2000"
	}

	if (!device.currentValue("motion") || state.pendingRefresh) {
		cmds << sensorBinaryGetCmd(12)
	}

	if (!device.currentValue("acceleration") || state.pendingRefresh) {
		cmds << sensorBinaryGetCmd(8)
	}

	cmds << batteryGetCmd()

	configParams.each {
		if (it == sendBasicSetParam) {
			it.value = (assocDNIsSetting ? 1 : 0)
		}
		cmds += updateConfigVal(it)
	}

	cmds += configureAssocs()

	state.pendingRefresh = false

	return cmds ? delayBetween(cmds, 500) : []
}

private updateConfigVal(param) {
	def result = []
	def configVal = state["configVal${param.num}"]

	if (state.pendingRefresh || ("${configVal}" != "${param.value}")) {
		logDebug "Changing ${param.name} (#${param.num}) from ${configVal} to ${param.value}"
		result << configSetCmd(param)
		result << configGetCmd(param)
	}
	return result
}


private configureAssocs() {
	def cmds = []

	def stateNodeIds = (state.assocNodeIds ?: [])
	def settingNodeIds = assocDNIsSettingNodeIds

	def newNodeIds = settingNodeIds.findAll { !(it in stateNodeIds)  }
	if (newNodeIds) {
		cmds << associationSetCmd(newNodeIds)
	}

	def oldNodeIds = stateNodeIds.findAll { !(it in settingNodeIds)  }
	if (oldNodeIds) {
		cmds << associationRemoveCmd(oldNodeIds)
	}

	if (cmds || state.pendingRefresh) {
		cmds << associationGetCmd()
	}

	return cmds
}

private getAssocDNIsSettingNodeIds() {
	def nodeIds = convertHexListToIntList(assocDNIsSetting?.split(","))

	if (assocDNIsSetting && !nodeIds) {
		log.warn "'${assocDNIsSetting}' is not a valid value for the 'Device Network Ids for Association' setting.  All z-wave devices have a 2 character Device Network Id and if you're entering more than 1, use commas to separate them."
	}
	else if (nodeIds?.size() > 5) {
		log.warn "The 'Device Network Ids for Association' setting contains more than 5 Ids so only the first 5 will be associated."
	}

	return nodeIds
}


def ping() {
	logDebug "ping()"
	return [versionGetCmd()]
}


def refresh() {
	initializePowerSource()

	if (state.pendingRefresh) {
		configParams.each {
			state."configVal${it.num}" = null
		}
	}

	state.pendingRefresh = true

	if (device.currentValue("powerSource") == "dc") {
		return configure()
	}
	else {
		logForceWakeupMessage "The sensor data will be refreshed the next time the device wakes up."
	}
}

private logForceWakeupMessage(msg) {
	log.warn "${msg}  You can force the device to wake up immediately by holding the z-button for 5 seconds."
}


def parse(String description) {
	def result = []
	try {
		def cmd = zwave.parse(description, commandClassVersions)
		if (cmd) {
			result += zwaveEvent(cmd)
		}
		else {
			logDebug "Unable to parse description: $description"
		}
	}
	catch (e) {
		log.error "$e"
	}
	return result
}


def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapCmd = cmd.encapsulatedCommand(commandClassVersions)

	def result = []
	if (encapCmd) {
		result += zwaveEvent(encapCmd)
	}
	else {
		log.warn "Unable to extract encapsulated cmd from $cmd"
	}
	return result
}


def zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd) {
	logDebug "Device Woke Up"

	initializePowerSource()

	def cmds = []

	if (!isDuplicateCommand(state.lastWakeUp, 10000)) {
		state.lastWakeUp = new Date().time
		cmds += configure()
	}

	if (cmds) {
		cmds << "delay 2000"
	}
	cmds << wakeUpNoMoreInfoCmd()

	return response(cmds)
}


def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
	logTrace "BatteryReport $cmd"
	def val = (cmd.batteryLevel == 0xFF ? 1 : cmd.batteryLevel)

	if (val > 100) val = 100
	if (val < 1) val = 1

	logDebug "Battery ${val}%"	
	sendEvent(getEventMap("battery", val, "%"))
	if (device.currentValue("powerSource") != "battery") {
		sendEvent(getEventMap("powerSource", "battery"))
	}
	return []
}


def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
	def subVersion = String.format("%02d", cmd.applicationSubVersion)
	def fullVersion = "${cmd.applicationVersion}.${subVersion}"
	logDebug "Firmware: ${fullVersion}"
	return []
}


def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
	logDebug "AssociationReport: ${cmd}"


	if (cmd.groupingIdentifier == 2) {
		state.assocNodeIds = cmd.nodeId

		def dnis = convertIntListToHexList(cmd.nodeId)?.join(", ") ?: "none"
		if (getDataValue("associatedDeviceNetworkIds") != dnis) {
			updateDataValue("associatedDeviceNetworkIds", dnis)
		}
	}
	return []
}


// Stores the configuration values so that it only updates them when they've changed or a refresh was requested.
def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
	logTrace "ConfigurationReport ${cmd}"

	def param = configParams.find { it.num == cmd.parameterNumber }
	if (param) {
		def val = cmd.scaledConfigurationValue

		if (val == 0) {
			if (param.num == shockAlarmParam.num) {
				sendAccelerationEvent("inactive")
			}
			else if (param.num == motionSensitivityParam.num) {
				sendMotionEvent("inactive")
			}
		}

		logDebug "${param.name} (#${param.num}) = ${val}"
		state."configVal${param.num}" = val
	}
	else {
		logDebug "Parameter ${cmd.parameterNumber}: ${cmd.configurationValue}"
	}

	state.isConfigured = true
	return []
}


def zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd) {
	logTrace "NotificationReport: $cmd"

	if (cmd.notificationType == 7) {
		switch (cmd.event) {
			case 0:
				if (cmd.eventParameter[0] == 3 || cmd.eventParameter[0] == 9) {
					sendAccelerationEvent("inactive")
				}
				else {
					sendMotionEvent("inactive")
				}
				break
			case { it == 3 || it == 9}:
				sendAccelerationEvent("active")
				break
			case 8:
				sendMotionEvent("active")
				break
			default:
				logDebug "Unknown Notification Event: ${cmd}"
		}
	}
	return []
}


def zwaveEvent(hubitat.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd) {
	logTrace "SensorBinaryReport: $cmd"

	switch (cmd.sensorType) {
		case 8:
			sendAccelerationEvent(cmd.sensorValue ? "active" : "inactive")
			break
		case 12:
			sendMotionEvent(cmd.sensorValue ? "active" : "inactive")
			break
		default:
			logDebug "Unknown Sensor Type: $cmd"
	}
	return []
}

private sendMotionEvent(value) {
	logDebug "Motion ${value}"
	sendEvent(getEventMap("motion", value))
}

private sendAccelerationEvent(value) {
	logDebug "Acceleration ${value}"
	sendEvent(getEventMap("acceleration", value))
}


def zwaveEvent(hubitat.zwave.Command cmd) {
	logDebug "Ignored Command: $cmd"
	return []
}

private getEventMap(name, value, unit=null, displayed=true) {
	def eventMap = [
		name: name,
		value: value,
		displayed: displayed,
		isStateChange: true
	]
	if (unit) {
		eventMap.unit = unit
	}
	return eventMap
}


private wakeUpNoMoreInfoCmd() {
	return secureCmd(zwave.wakeUpV2.wakeUpNoMoreInformation())
}

private batteryGetCmd() {
	return secureCmd(zwave.batteryV1.batteryGet())
}

private versionGetCmd() {
	return secureCmd(zwave.versionV1.versionGet())
}

private sensorBinaryGetCmd(sensorType) {
	return secureCmd(zwave.sensorBinaryV2.sensorBinaryGet(sensorType: sensorType))
}

private configGetCmd(param) {
	return secureCmd(zwave.configurationV1.configurationGet(parameterNumber: param.num))
}

private configSetCmd(param) {
	return secureCmd(zwave.configurationV1.configurationSet(parameterNumber: param.num, size: param.size, scaledConfigurationValue: param.value))
}

private associationSetCmd(nodes) {
	return secureCmd(zwave.associationV2.associationSet(groupingIdentifier: 2, nodeId: nodes))
}

private associationRemoveCmd(nodes) {
	return secureCmd(zwave.associationV2.associationRemove(groupingIdentifier: 2, nodeId: nodes))
}

private associationGetCmd() {
	return secureCmd(zwave.associationV2.associationGet(groupingIdentifier: 2))
}

private secureCmd(cmd) {
	if (getDataValue("zwaveSecurePairingComplete") == "true") {
		return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
	}
	else {	
		return cmd.format()
	}
}


// Configuration Parameters
private getConfigParams() {
	[
		motionSensitivityParam,
		sendBasicSetParam,
		basicSetValueParam,
		shockAlarmParam,
		motionClearedDelayParam,
		sensorBinaryReportsParam,
		ledParam,
		lowBatteryAlarmParam
	]
}

private getMotionSensitivityParam() {
	return getParam(12, "Motion Sensitivity", 1, 8, motionSensitivityOptions)
}

private getSendBasicSetParam() {
	return getParam(14, "Send Basic Set", 1, 0, enabledDisabledOptions)
}

private getBasicSetValueParam() {
	return getParam(15, "Association Basic Set Value", 1, 0, [
		"0": "Active: 0xFF / Inactive: 0x00",
		"1": "Active: 0x00 / Inactive: 0xFF"
	])
}

private getShockAlarmParam() {
	return getParam(17, "Shock Alarm", 1, 1, enabledDisabledOptions)
}

private getMotionClearedDelayParam() {
	return getParam(18, "Motion Cleared Delay", 2, 30, motionClearedDelayOptions)
}

private getSensorBinaryReportsParam() {
	return getParam(19, "Sensor Binary Reports", 1, 0, enabledDisabledOptions)
}

private getLedParam() {
	return getParam(20, "Motion LED", 1, 1, enabledDisabledOptions)
}

private getLowBatteryAlarmParam() {
	return getParam(32, "Low Battery Level", 1, 10, ["10":"10%","25":"25%","50":"50%"])
}

private getParam(num, name, size, defaultVal, options) {
	def val = safeToInt((settings ? settings["configParam${num}"] : null), defaultVal)

	def map = [num: num, name: name, size: size, defaultValue: defaultVal, value: val]

	map.options = options?.collect { k, v ->
		if ("${k}" == "${defaultVal}") {
			v = "${v} [DEFAULT]"
		}
		["$k": "$v"]
	}

	return map
}


private getMotionSensitivityOptions() {
	def options = [
		"0": "Disabled",
		"1": "1 (Least Sensitive)"
	]

	(2..7).each {
		options["${it}"] = "${it}"
	}

	options["8"] = "8 (Most Sensitive)"
	return options
}

private getMotionClearedDelayOptions() {
	[
		"0": "0 Seconds",
		"1": "1 Seconds",
		"2": "2 Seconds",
		"3": "3 Seconds",
		"4": "4 Seconds",
		"5": "5 Seconds",
		"10": "10 Seconds",
		"15": "15 Seconds",
		"30": "30 Seconds",
		"45": "45 Seconds",
		"60": "1 Minute",
		"120": "2 Minutes",
		"180": "3 Minutes",
		"240": "4 Minutes",
		"300": "5 Minutes",
		"420": "7 Minutes",
		"600": "10 Minutes",
		"900": "15 Minutes",
		"1800": "30 Minutes",
		"3600": "60 Minutes"
	]
}

private getEnabledDisabledOptions() {
	[
		"0": "Disabled",
		"1": "Enabled"
	]
}


private convertIntListToHexList(intList) {
	def hexList = []
	intList?.each {
		hexList.add(Integer.toHexString(it).padLeft(2, "0").toUpperCase())
	}
	return hexList
}

private convertHexListToIntList(String[] hexList) {
	def intList = []

	hexList?.each {
		try {
			it = it.trim()
			intList.add(Integer.parseInt(it, 16))
		}
		catch (e) { }
	}
	return intList
}

private safeToInt(val, defaultVal=0) {
	return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}


private convertToLocalTimeString(dt) {
	try {
		def timeZoneId = location?.timeZone?.ID
		if (timeZoneId) {
			return dt.format("MM/dd/yyyy hh:mm:ss a", TimeZone.getTimeZone(timeZoneId))
		}
		else {
			return "$dt"
		}
	}
	catch (e) {
		return "$dt"
	}
}

private isDuplicateCommand(lastExecuted, allowedMil) {
	!lastExecuted ? false : (lastExecuted + allowedMil > new Date().time)
}

private logDebug(msg) {
	if (settings?.debugOutput || settings?.debugOutput == null) {
		log.debug "$msg"
	}
}

private logTrace(msg) {
	 // log.trace "$msg"
}
