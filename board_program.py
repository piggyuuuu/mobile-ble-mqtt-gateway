import time
from machine import Timer, Pin, I2C
from board import LED 
import time 
from ubluepy import (
    Service,
    Characteristic,
    UUID,
    Peripheral,
    constants,
)


#温湿度
import hs3003
bus = I2C(1, scl=Pin(15), sda=Pin(14))
hs = hs3003.HS3003(bus)    
temlist=0

def read_temp_hum(t):
    global temlist
    rH   = hs.humidity()       # 相对湿度 百分比
    t  = hs.temperature()
    temlist=t
    msg = f"Sample:{temlist}°C"
    
    if notif_enabled:
        #print(msg)
        custom_read_char.write(msg.encode('utf-8')) #一次发不了很长数据
    # print("rH: %.2f%% " % (rH))

tim1 = Timer(1,
             period=500_000,               
             mode=Timer.PERIODIC,
             callback=read_temp_hum)

####LED#########
led_r = LED(1)  # 红灯
led_g = LED(2)  # 绿灯
led_b = LED(3)  # 蓝灯
current_led = None
state=False

# 添加LED状态跟踪
led_states = {
    'R': False,  # 红灯是否正在闪烁
    'G': False,  # 绿灯是否正在闪烁
    'B': False   # 蓝灯是否正在闪烁
}

def blink_cb(timer):
    global state
    state = not state
    # print(current_led)
    if current_led:
        print(current_led)
        if state:
            current_led.on()
        else:
            current_led.off()

tim3 = Timer(3, period=500_000, mode=Timer.PERIODIC, callback=blink_cb)
tim3.start()

def start_blink(led):
    global current_led, state
    # 先关掉所有灯
    led_r.off()
    led_g.off()
    led_b.off()
    # 重置状态
    state = False
    current_led = led
    print(f"开始闪烁: {current_led}")
    
def stop_blink():
    global current_led
    print("停止所有闪烁")
    led_r.off()
    led_g.off()
    led_b.off()
    # 取消 current_led 引用
    current_led = None

def toggle_led(led_key, led_obj):
    """切换LED的闪烁状态"""
    global led_states
    
    if led_states[led_key]:
        # 当前正在闪烁，停止闪烁
        print(f"停止{led_key}灯闪烁")
        stop_blink()
        led_states[led_key] = False
        # 重置所有状态
        for key in led_states:
            led_states[key] = False
    else:
        # 当前未闪烁，开始闪烁
        print(f"开始{led_key}灯闪烁")
        # 先停止其他灯的闪烁状态
        for key in led_states:
            led_states[key] = False
        # 开始当前灯的闪烁
        led_states[led_key] = True
        start_blink(led_obj)

def event_handler(id, handle, data):
    global periph
    global services
    global custom_read_char
    global notif_enabled
    global temlist

    if id == constants.EVT_GAP_CONNECTED:
        print("Connected")
    elif id == constants.EVT_GAP_DISCONNECTED:
        print(" Disconnected, restarting adv")
        periph.advertise(device_name="NanoBowen")
    elif id == constants.EVT_GATTS_WRITE:
        if handle == 16:  # custom_wrt_char
            # 解码客户端写入的数据
            text = data.decode('utf-8').strip()
            print(f"接收到命令: {text}")

            # 如果已使能通知，就原样通知回去
            if notif_enabled:
                custom_read_char.write(data)
                
            if text == 'R':
                # R键切换红灯闪烁
                toggle_led('R', led_r)
            elif text == 'Y' or text == 'G':
                # Y或G键切换绿灯闪烁（因为板子没有黄灯，用绿灯代替）
                toggle_led('G', led_g)
            elif text == 'B':
                # B键切换蓝灯闪烁
                toggle_led('B', led_b)
            else:
                print(f"未知命令: {text}")
                
        elif handle == 19:  # CCCD of custom_read_char
            # 客户端写入 1 开启通知，写入 0 关闭
            notif_enabled = (int(data[0]) == 1)

# Initial states
notif_enabled = False

# UUIDs for custom service and characteristics
custom_svc_uuid       = UUID("4A981234-1CC4-E7C1-C757-F1267DD021E8")
custom_wrt_char_uuid  = UUID("4A981235-1CC4-E7C1-C757-F1267DD021E8")
custom_read_char_uuid = UUID("4A981236-1CC4-E7C1-C757-F1267DD021E8")

# Build service and characteristics
custom_svc        = Service(custom_svc_uuid)
custom_wrt_char   = Characteristic(
    custom_wrt_char_uuid,
    props=Characteristic.PROP_WRITE
)
custom_read_char  = Characteristic(
    custom_read_char_uuid,
    props=Characteristic.PROP_READ | Characteristic.PROP_NOTIFY,
    attrs=Characteristic.ATTR_CCCD
)

custom_svc.addCharacteristic(custom_wrt_char)
custom_svc.addCharacteristic(custom_read_char)

# Setup peripheral
periph = Peripheral()
periph.addService(custom_svc)
periph.setConnectionHandler(event_handler)
periph.advertise(device_name="NanoBowen")
tim1.start()

print("BLE设备已启动，等待连接...")
print("支持的命令:")
print("  R - 切换红灯闪烁")
print("  Y/G - 切换绿灯闪烁")
print("  B - 切换蓝灯闪烁")

# Main loop
while True:
    time.sleep_ms(500)