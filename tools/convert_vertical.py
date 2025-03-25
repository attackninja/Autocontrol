import re

input_file = "E:\\yunye11x.txt"
output_file = "E:\\yunye11.txt"
screen_width = 2400
screen_height = 1080
raw_max = 4096  # 调整为 4096，可能更准确
x_scale, y_scale = 1.0, 1.0  # 可调比例
x_offset, y_offset = 0.0, 0.0  # 可调偏移

with open(input_file, "r") as infile, open(output_file, "w") as outfile:
    pointers = {}
    current_slot = 0
    prev_timestamp = None

    for line in infile:
        match = re.match(r"\[\s*(\d+\.\d+)\]\s+(/\w+/\w+/\w+):\s+(\w+)\s+(\w+)\s+(\w+)", line)
        if match:
            timestamp, _, event_type, code, value = match.groups()
            timestamp = float(timestamp)

            if event_type == "EV_ABS":
                if code == "ABS_MT_SLOT":
                    current_slot = int(value, 16)
                elif code == "ABS_MT_TRACKING_ID":
                    tracking_id = int(value, 16) if value != "ffffffff" else -1
                    if tracking_id == -1 and current_slot in pointers:
                        pointer = pointers.pop(current_slot)
                        total_duration = (timestamp - pointer['start_time']) * 1000
                        pointer_id = current_slot + 1
                        outfile.write(f"UP:{pointer_id}:{pointer['x']:.1f}:{pointer['y']:.1f}:{total_duration:.6f}:{timestamp:.6f}\n")
                    elif tracking_id != -1 and current_slot not in pointers:
                        pointers[current_slot] = {'x': None, 'y': None, 'start_time': None, 'tracking_id': tracking_id, 'last_event_time': None}
                elif code == "ABS_MT_POSITION_X" and current_slot in pointers:
                    raw_x = int(value, 16)
                    pointers[current_slot]['y'] = screen_height - (raw_x / raw_max) * screen_height * y_scale + y_offset
                elif code == "ABS_MT_POSITION_Y" and current_slot in pointers:
                    raw_y = int(value, 16)
                    pointers[current_slot]['x'] = (raw_y / raw_max) * screen_width * x_scale + x_offset

            elif event_type == "EV_SYN" and code == "SYN_REPORT":
                for slot in list(pointers.keys()):
                    pointer = pointers[slot]
                    if pointer['x'] is not None and pointer['y'] is not None:
                        pointer_id = slot + 1
                        duration = (timestamp - pointer['last_event_time']) * 1000 if pointer['last_event_time'] is not None else 0.0

                        if pointer['start_time'] is None:
                            pointer['start_time'] = timestamp
                            pointer['last_event_time'] = timestamp
                            outfile.write(f"DOWN:{pointer_id}:{pointer['x']:.1f}:{pointer['y']:.1f}:{timestamp:.6f}\n")
                        elif prev_timestamp is not None:
                            outfile.write(f"MOVE:{pointer_id}:{pointer['x']:.1f}:{pointer['y']:.1f}:{duration:.6f}:{timestamp:.6f}\n")
                            pointer['last_event_time'] = timestamp

            prev_timestamp = timestamp

print(f"转换完成，结果保存至 {output_file}")