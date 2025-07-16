// script.js
// 依赖：Web Bluetooth API, AWS SDK for JavaScript v2, Paho MQTT over WebSocket
(() => {
  const page = document.title.trim();

  // 全局状态
  let bleDevice = null;
  let bleServer = null;
  let mqttClient = null;

  // —— BLE 扫描与连接 —————————————————————————————
  async function scanBLE() {
    try {
      const device = await navigator.bluetooth.requestDevice({
        acceptAllDevices: true,
        optionalServices: ['generic_access']  // 可根据需要加 UUID
      });
      bleDevice = device;
      document.querySelector('.device-list').innerHTML = `
        <div class="device-item">
          <span>${device.name || device.id}</span>
          <span>RSSI: N/A</span>
        </div>`;
    } catch (err) {
      alert('BLE scan failed: ' + err);
    }
  }

  async function connectBLE() {
    if (!bleDevice) return alert('请先扫描并选择一个设备');
    try {
      bleServer = await bleDevice.gatt.connect();
      updateStatus('BLE', true);
    } catch (err) {
      alert('BLE connect failed: ' + err);
      updateStatus('BLE', false);
    }
  }

  // —— GATT 读写 ——————————————————————————————————————————————————
  async function readCharacteristic() {
    const svcUuid = document.getElementById('svcUuid').value;
    const charUuid = document.getElementById('charUuid').value;
    if (!bleServer) return alert('请先连接 BLE');
    try {
      const svc = await bleServer.getPrimaryService(svcUuid);
      const chr = await svc.getCharacteristic(charUuid);
      const val = await chr.readValue();
      alert('Read: ' + new TextDecoder().decode(val));
    } catch (err) {
      alert('Read failed: ' + err);
    }
  }

  async function writeCharacteristic() {
    const svcUuid = document.getElementById('svcUuid').value;
    const charUuid = document.getElementById('charUuid').value;
    const data = prompt('请输入要写入的文本');
    if (!data) return;
    try {
      const svc = await bleServer.getPrimaryService(svcUuid);
      const chr = await svc.getCharacteristic(charUuid);
      const buf = new TextEncoder().encode(data);
      await chr.writeValue(buf);
      alert('Write OK');
    } catch (err) {
      alert('Write failed: ' + err);
    }
  }

  // —— AWS IoT 连接 —————————————————————————————————————————————
  async function connectAWS() {
    const endpoint = document.getElementById('endpoint').value;
    const clientId = document.getElementById('clientId').value;
    const cognitoId = document.getElementById('cognitoId').value;

    AWS.config.region = endpoint.split('.')[1];  // e.g. a1xyz.iot.<region>.amazonaws.com
    AWS.config.credentials = new AWS.CognitoIdentityCredentials({
      IdentityPoolId: cognitoId
    });
    try {
      await AWS.config.credentials.getPromise();
      updateStatus('AWS IoT', true);
      // 使用 Paho 建立 WebSocket 连接（SigV4 签名示例略）
      mqttClient = new Paho.MQTT.Client(
        endpoint.replace(/^wss?:\/\//, ''),
        443,
        clientId
      );
      mqttClient.connect({
        useSSL: true,
        onSuccess: () => updateStatus('AWS IoT', true),
        onFailure: e => { updateStatus('AWS IoT', false); console.error(e); },
        // TODO: SigV4 签名回调：userName/password
      });
    } catch (err) {
      alert('AWS credentials failed: ' + err);
      updateStatus('AWS IoT', false);
    }
  }

  // —— 实时数据显示 —————————————————————————————————————————————
  function subscribeRealtime() {
    if (!mqttClient || !mqttClient.isConnected()) return;
    mqttClient.subscribe('test/comp6733', {
      onMessageArrived: msg => {
        appendLog(`[${new Date().toLocaleTimeString()}] ${msg.payloadString}`);
        updateStatus('Last Message', true);
      }
    });
  }

  function pauseRealtime() {
    if (mqttClient) mqttClient.unsubscribe('test/comp6733');
    updateStatus('Last Message', false);
  }

  function clearLog() {
    document.querySelector('.log').innerHTML = '';
  }

  // —— UI 帮助函数 —————————————————————————————————————————————
  function updateStatus(key, ok) {
    const rows = document.querySelectorAll('.status');
    rows.forEach(r => {
      if (r.textContent.includes(key)) {
        const val = r.querySelector('div:nth-child(2)');
        val.textContent = ok ? '✅ Connected' : '❌ Disconnected';
        val.className = ok ? 'ok' : 'bad';
      }
    });
  }

  function appendLog(txt) {
    const log = document.querySelector('.log');
    log.innerHTML += txt + '<br/>';
    log.scrollTop = log.scrollHeight;
  }

  // —— 事件挂载 —————————————————————————————————————————————
  document.addEventListener('DOMContentLoaded', () => {
    switch (page) {
      case 'BLE Device Scan':
        document.querySelector('.secondary').onclick = scanBLE;
        document.querySelector('.primary').onclick = connectBLE;
        break;
      case 'GATT Configuration':
        document.querySelector('.secondary').onclick = readCharacteristic;
        document.querySelector('.primary').onclick = writeCharacteristic;
        break;
      case 'AWS IoT Settings':
        document.querySelector('.secondary').onclick = () => {}; // Load Cert 留空或自定义
        document.querySelector('.primary').onclick = connectAWS;
        break;
      case 'Real-time Data':
        document.querySelector('.secondary').onclick = pauseRealtime;
        document.querySelector('.primary').onclick = clearLog;
        // 页面载入后自动订阅
        setTimeout(subscribeRealtime, 1000);
        break;
    }
  });
})();
