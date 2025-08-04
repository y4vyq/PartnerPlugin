# PartnerPlugin - 我的世界伙伴插件

一个允许玩家召唤和管理伙伴实体的Minecraft插件，支持自定义伙伴类型、粒子效果和生命周期管理。

## 功能特点

- 召唤自定义生物作为伙伴（默认狐狸，可通过配置修改）
- 伙伴自动跟随玩家，支持跨世界传送
- 可隐藏/显示伙伴实体
- 多种粒子效果可选（默认、螺旋、圆形、自定义配置）
- 伙伴无敌且不会受到伤害
- 可配置伙伴持续时间、名称和属性
- 支持权限管理和配置重载

## 安装方法

1. 确保你的服务器已安装 [Spigot/Paper](https://www.spigotmc.org/) 服务端
2. 下载最新版本的 `PartnerPlugin.jar`
3. 将jar文件放入服务器的 `plugins` 文件夹
4. 重启服务器或使用 `/plugman load PartnerPlugin` 加载插件
5. 插件会自动生成配置文件，位于 `plugins/PartnerPlugin/` 目录下

## 命令说明

| 命令 | 描述 | 权限 |
|------|------|------|
| `/partner` | 召唤或隐藏你的伙伴 | 无 |
| `/partner help` | 显示帮助信息 | 无 |
| `/partner hide` | 隐藏当前伙伴 | 无 |
| `/partner show` | 显示已隐藏的伙伴 | 无 |
| `/partner particle <类型>` | 切换伙伴召唤时的粒子效果 | 无 |
| `/partner reload` | 重载插件配置 | `partner.reload` |

粒子效果类型：`default`（默认）、`spiral`（螺旋）、`circle`（圆形）、`custom`（自定义配置）

## 配置说明

插件主要配置文件为 `config.yml`，位于插件目录下：

```yaml
# 伙伴基础设置
partner:
  entity-type: FOX  # 伙伴实体类型
  duration: 300     # 伙伴持续时间（秒）
  name: "&6&l我的伙伴"  # 伙伴名称（支持颜色代码）
  show-name: true   # 是否显示名称

# 召唤效果配置
summon-effect:
  sound: "ENTITY_FOX_AMBIENT"  # 召唤音效
  volume: 1.0                  # 音量
  pitch: 1.0                   # 音调

# 粒子效果配置（当粒子生成器为custom时生效）
particle-effect:
  generator-type: default  # 粒子生成器类型
  particle: FLAME          # 粒子类型
  count: 100               # 粒子数量
  radius: 1.5              # 半径范围
  duration: 20             # 效果持续时间（tick）
  height-multiplier: 0.5   # 高度乘数
  points-per-tick: 10      # 每tick生成的粒子数

# 实体属性配置
entity-attributes:
  health: 20.0     # 生命值
  ai: true         # 是否启用AI
  glowing: false   # 是否发光
  silent: false    # 是否静音
  age: true        # true=成年, false=幼年
```

## 权限设置

| 权限节点 | 描述 | 默认 |
|---------|------|------|
| `partner.reload` | 允许重载配置 | OP |

## 兼容性

- 支持 Minecraft 1.20 版本
- 需使用 Java 17 及以上版本运行

## 开发与构建

1. 克隆仓库：`git clone https://github.com/y4vyq/PartnerPlugin.git`
2. 使用 Maven 构建：`mvn clean package`
3. 构建产物位于 `target/` 目录下

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件
