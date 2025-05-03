import bpy
import json
import os # 导入os模块来处理路径
import math # 导入math模块用于转换角度

def get_ik_chain_bones(start_bone, chain_length):
    """
    从指定的骨骼开始，向上追溯父级骨骼，收集IK链中的骨骼对象。
    :param start_bone: IK约束应用到的骨骼 (bpy.types.PoseBone)
    :param chain_length: IK约束的chain_count值 (0表示到根骨骼)
    :return: 一个包含链中 PoseBone 对象的列表 (从根部到末端排序)
    """
    chain_bone_objects = [] # 改为存储 PoseBone 对象
    current_bone = start_bone
    count = chain_length

    # 当链长为0时，一直向上遍历直到没有父级；
    # 当链长>0时，向上遍历指定数量的骨骼。
    while current_bone and (count > 0 or chain_length == 0):
        chain_bone_objects.append(current_bone) # 存储对象本身
        if count > 0:
            count -= 1  # 减少计数器
        # 如果链长为0且当前骨骼没有父级，则停止 (已到根)
        if chain_length == 0 and not current_bone.parent:
            break
        current_bone = current_bone.parent  # 移动到父骨骼

    # 列表当前是 [末端, ..., 根部]，反转它得到 [根部, ..., 末端] 的顺序
    chain_bone_objects.reverse()
    return chain_bone_objects

def get_bone_ik_limits(bone):
    """
    提取单个 PoseBone 的 IK 相关限制和设置。
    :param bone: bpy.types.PoseBone 对象
    :return: 包含该骨骼IK限制信息的字典
    """
    rad_to_deg = 180 / math.pi
    limits = {
        "name": bone.name, # 骨骼名称，方便确认
        # X轴旋转限制
        "limit_rotation_x": bone.use_ik_limit_x,
        "min_rotation_x_rad": bone.ik_min_x if bone.use_ik_limit_x else None,
        "min_rotation_x_deg": bone.ik_min_x * rad_to_deg if bone.use_ik_limit_x else None,
        "max_rotation_x_rad": bone.ik_max_x if bone.use_ik_limit_x else None,
        "max_rotation_x_deg": bone.ik_max_x * rad_to_deg if bone.use_ik_limit_x else None,
        # Y轴旋转限制
        "limit_rotation_y": bone.use_ik_limit_y,
        "min_rotation_y_rad": bone.ik_min_y if bone.use_ik_limit_y else None,
        "min_rotation_y_deg": bone.ik_min_y * rad_to_deg if bone.use_ik_limit_y else None,
        "max_rotation_y_rad": bone.ik_max_y if bone.use_ik_limit_y else None,
        "max_rotation_y_deg": bone.ik_max_y * rad_to_deg if bone.use_ik_limit_y else None,
        # Z轴旋转限制
        "limit_rotation_z": bone.use_ik_limit_z,
        "min_rotation_z_rad": bone.ik_min_z if bone.use_ik_limit_z else None,
        "min_rotation_z_deg": bone.ik_min_z * rad_to_deg if bone.use_ik_limit_z else None,
        "max_rotation_z_rad": bone.ik_max_z if bone.use_ik_limit_z else None,
        "max_rotation_z_deg": bone.ik_max_z * rad_to_deg if bone.use_ik_limit_z else None,
        # 刚度 (Stiffness) - 直接获取值，0表示无刚度
        "stiffness_x": bone.ik_stiffness_x,
        "stiffness_y": bone.ik_stiffness_y,
        "stiffness_z": bone.ik_stiffness_z,
        # 拉伸 (Stretch) - 注意：use_stretch 是约束属性，ik_stretch 是骨骼属性
        "ik_stretch": bone.ik_stretch # 0.0 到 1.0
        # 注意：Blender API 中似乎没有直接对应于骨骼面板IK设置中的位置限制(Lock X/Y/Z Location)，
        # IK约束本身的 lock_location_* 更像是作用于目标骨骼的。
        # 如果需要位置锁定，可能需要检查其他约束类型或骨骼属性。
    }
    return limits

def export_ik_constraints_to_json(filepath):
    """
    遍历场景中的所有骨架对象，提取其姿态骨骼上的IK约束信息，
    包括构成IK链的所有骨骼名称及其各自的IK限制，并将其导出为JSON文件。
    """
    all_ik_data = [] # 用于存储所有找到的IK约束信息

    print("开始扫描场景中的骨架对象...")

    # 遍历场景中的所有对象
    for obj in bpy.context.scene.objects:
        # 检查对象是否是骨架类型 (ARMATURE) 并且具有姿态数据 (pose)
        if obj.type == 'ARMATURE' and obj.pose:
            armature_name = obj.name
            print(f"  找到骨架: {armature_name}")

            # 遍历该骨架的所有姿态骨骼 (Pose Bones)
            for ik_end_bone in obj.pose.bones:
                bone_name = ik_end_bone.name
                # 遍历当前姿态骨骼上的所有约束
                for constraint in ik_end_bone.constraints:
                    # 检查约束类型是否为 'IK'
                    if constraint.type == 'IK':
                        print(f"    找到骨骼 '{bone_name}' 上的IK约束: {constraint.name}")

                        # --- 获取IK链上的 PoseBone 对象 ---
                        ik_chain_pose_bones = get_ik_chain_bones(ik_end_bone, constraint.chain_count)
                        ik_chain_names = [b.name for b in ik_chain_pose_bones] # 获取骨骼名称列表
                        print(f"      IK链骨骼: {ik_chain_names}")

                        # --- 提取链中每个骨骼的IK限制 ---
                        chain_bone_limits_map = {}
                        for bone_in_chain in ik_chain_pose_bones:
                            limits = get_bone_ik_limits(bone_in_chain)
                            chain_bone_limits_map[bone_in_chain.name] = limits
                            print(f"        提取骨骼 '{bone_in_chain.name}' 的IK限制...")
                        # --- 结束提取限制 ---

                        # 提取IK约束的详细信息
                        constraint_info = {
                            "armature_name": armature_name, # 约束所在的骨架名称
                            "constraint_target_bone": bone_name, # 约束作用的目标骨骼 (链末端)
                            "constraint_name": constraint.name, # 约束自身的名称

                            # --- 新增：链中骨骼的IK限制映射 ---
                            "ik_chain_bone_limits": chain_bone_limits_map,
                            # --- 结束新增 ---

                            # 目标对象和骨骼
                            "target_object": constraint.target.name if constraint.target else None,
                            "target_bone": constraint.subtarget if constraint.target and constraint.subtarget else None,

                            # 极向目标对象和骨骼
                            "pole_target_object": constraint.pole_target.name if constraint.pole_target else None,
                            "pole_target_bone": constraint.pole_subtarget if constraint.pole_target and constraint.pole_subtarget else None,

                            # IK链设置
                            "chain_length": constraint.chain_count, # IK影响的骨骼链长度 (0表示一直到根骨骼)
                            "pole_angle_rad": constraint.pole_angle, # 极向角度 (弧度)
                            "pole_angle_deg": constraint.pole_angle * (180 / math.pi), # 极向角度 (度) - 使用 math.pi
                            "iterations": constraint.iterations,   # 求解器迭代次数
                            "use_tail": constraint.use_tail,       # 是否控制骨骼末端
                            "use_stretch": constraint.use_stretch,   # 是否允许拉伸
                            "influence": constraint.influence      # 约束的影响力 (0.0 到 1.0)
                            # 可以根据需要添加更多属性, 例如:
                            # "ik_type": constraint.ik_type, # IK 求解器类型
                            # "lock_location_x/y/z": constraint.lock_location[0/1/2], # 轴锁定 (Blender 4.x+)
                            # "limit_location_x/y/z": constraint.limit_location[0/1/2], # 位置限制 (Blender 4.x+)
                        }
                        # 将提取到的约束信息添加到总列表中
                        all_ik_data.append(constraint_info)
                    # else: # 如果需要检查其他类型的约束，可以在这里添加逻辑
                    #    print(f"    骨骼 '{bone_name}' 上的非IK约束: {constraint.name} (类型: {constraint.type})")


    # 检查是否收集到了IK数据
    if not all_ik_data:
        print("警告: 在场景中没有找到任何IK约束。")
        return False

    print(f"\n总共找到 {len(all_ik_data)} 个IK约束。准备写入JSON文件...")

    # 尝试将收集到的数据写入指定的JSON文件
    try:
        # 获取文件路径的目录部分
        output_dir = os.path.dirname(filepath)
        # 如果目录不存在且路径不为空，则创建目录
        if output_dir and not os.path.exists(output_dir):
            os.makedirs(output_dir)
            print(f"已创建输出目录: {output_dir}")

        # 以写入模式打开文件 (w)，使用utf-8编码，启用缩进以方便阅读
        with open(filepath, 'w', encoding='utf-8') as f:
            # 使用json.dump将Python列表转换为JSON格式并写入文件
            # indent=4 使输出的JSON格式化，更易读
            # ensure_ascii=False 确保中文字符能正确写入，而不是被转义
            json.dump(all_ik_data, f, ensure_ascii=False, indent=4)

        print(f"IK约束信息已成功导出到: {filepath}")
        return True # 返回成功标志
    except Exception as e:
        # 如果写入过程中发生任何错误，打印错误信息
        print(f"错误: 写入JSON文件失败。路径: {filepath}")
        print(f"详细错误信息: {e}")
        return False # 返回失败标志

# --- 如何使用: ---
# 1. 在Blender中打开你的.blend文件。
# 2. 切换到 "Scripting" 工作区。
# 3. 创建一个新的文本文件 (点击 "+ New")。
# 4. 将上面的整个脚本粘贴到文本编辑器中。
# 5. !!! 修改下面的 'output_path' 为你想要保存JSON文件的完整路径 !!!
#    确保路径中的文件夹存在，或者脚本会尝试创建它。
#    示例:
#    Windows: output_path = r"C:/Users/你的用户名/Desktop/模型IK数据.json" (注意前面的 r 防止反斜杠转义)
#    macOS/Linux: output_path = "/Users/你的用户名/Desktop/模型IK数据.json"

# <<<<<<<<<<<<<<<<<<<< 在这里修改为你想要的输出文件路径 >>>>>>>>>>>>>>>>>>>>
output_path = "F:/Work/code/test/Spark-Core/src/main/resources/data/minecraft/geo/ik_constraints/steve_ik_with_limits.json" # 例如: "C:/path/to/your/ik_constraints.json"
# <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

# 执行导出函数
export_ik_constraints_to_json(output_path)
