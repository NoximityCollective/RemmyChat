# RemmyChat

**RemmyChat** is a lightweight, feature-rich chat management solution for PaperMC servers that enhances player communication with a clean, modern design.

<img src="https://img.shields.io/badge/Version-1.4.6-green" alt="Version"> <img src="https://img.shields.io/badge/License-GPL--3.0-orange" alt="License"> <img src="https://img.shields.io/badge/Supporterd MC Version-1.21.8-aqua" alt="Supporterd MC Version"> <a href="https://remmychat.noximity.com"><img src="https://img.shields.io/badge/Documentation-Wiki-brightgreen" alt="Documentation"></a>

## Overview

RemmyChat transforms your server's communication with a sleek, modern interface while providing powerful customization through MiniMessage formatting. From immersive proximity chat to comprehensive anti-spam features, RemmyChat balances simplicity with functionality to create the perfect chat experience.

![Overview Image](https://cdn.modrinth.com/data/kcImu7Wi/images/75ae522ff749334639ce6184a3e2e22d92e9e991.jpeg)

## Features

- **Advanced MiniMessage Formatting** — Rich text with colors, gradients, hover effects, and clickable elements
- **Multi-Channel System** — Global, local, staff, and trade channels with completely customizable formats
- **Proximity Chat** — Configurable radius-based local chat for immersive gameplay
- **Private Messaging System** — Seamless player-to-player communication with reply functionality
- **Message Toggle** — Allow players to enable/disable private messages
- **Social Spy** — Staff monitoring of player private messages
- **Flexible Permission System** — Granular control over all plugin features and channels
- **Group-Based Formatting** — Different chat formats based on player permissions
- **Custom Placeholders** — Create reusable text elements for consistency across formats
- **Message Placeholder Parsing** — Optional parsing of custom placeholders in chat messages themselves
- **Template System** — Hover templates, channel prefixes, and name styles for easy configuration
- **Interactive Elements** — Hoverable player names with customizable information tooltips
- **Automatic URL Detection** — Link formatting with configurable click-to-open functionality
- **Chat Cooldown** — Configurable anti-spam system to prevent message flooding
- **Message Persistence** — Database storage for player preferences and chat history
- **PlaceholderAPI Integration** — Unlimited customization possibilities
- **LuckPerms Integration** — Seamless permission management
- **Optimized Performance** — Minimal resource usage even on busy servers

![Features Image](https://cdn.modrinth.com/data/kcImu7Wi/images/1155be08ccbcc31c638d9daedcc38b8e581c200e.jpeg)

## Symbols & Emoji Replacement

RemmyChat supports custom symbols and emoji replacement in chat messages. You can define your own codes (like :smile:, :diamond:, etc.) and their replacements (emoji, text, or MiniMessage) in a separate `symbols.yml` file. This feature works with both Unicode emojis and custom resource pack textures.

![Symbols GIF](https://cdn.modrinth.com/data/kcImu7Wi/images/fbf28197353848d8962d7f8d813a7836d850ad51.gif)

### How it works
- Players can type codes like `:smile:` in chat.
- The plugin will automatically replace these codes with whatever you configure in `symbols.yml`.
- Supports case-insensitive codes and dashes (e.g., `:smile-face:`).
- Works seamlessly with resource packs for custom textures and emojis.

### Example `symbols.yml`
```yaml
symbols:
  ":smile:": "😄"
  ":heart:": "❤️"
  ":star:": "⭐"
  ":smile-face:": "😊"
```

You can add as many codes as you want. The replacement can be any string, emoji, or MiniMessage component. Resource pack textures will display correctly for players using the appropriate resource pack.

## Getting Started

Installation is straightforward:

1. Download the latest RemmyChat release
2. Place the JAR file in your server's `plugins` folder
3. Restart your server
4. Configuration files will be generated automatically

For detailed setup instructions and advanced configuration, visit our [official documentation](https://remmychat.noximity.com).

![Getting Started Image](https://cdn.modrinth.com/data/kcImu7Wi/images/48e4546417fe67875a2507bde87e93be6a266198.jpeg)

## Configuration

RemmyChat's configuration is highly flexible while remaining intuitive. Here's a sample of what you can accomplish:

### Channel Configuration

```yaml
# Channel configurations
channels:
  global:
    permission: "" # Empty means everyone can use
    radius: -1     # -1 means global chat
    prefix: ""     # No prefix for global
    hover: "player-info"
    display-name: ""

  local:
    permission: "remmychat.channel.local"
    radius: 100    # Chat radius in blocks
    prefix: "local" 
    hover: "local-chat"
    display-name: "<gray>[Local]</gray>"

  staff:
    permission: "remmychat.channel.staff"
    radius: -1
    prefix: "staff"
    hover: "staff-chat"
    display-name: "<gold>[Staff]</gold>"
```

### Custom Formatting

```yaml
# Group-based formatting with customizable styles
groups:
  admin:
    name-style: "admin"
    prefix: ""
    format: "%admin-hover% %player_name%: %default-message%"

  vip:
    name-style: "vip"
    prefix: ""
    format: "<click:suggest_command:'/msg %player_name%'><hover:show_text:'VIP Player'>%vip-prefix%</hover></click> %player_name%: %default-message%"
```

### Interactive Templates

```yaml
# Templates for reuse across formats
templates:
  hovers:
    player-info: "<#778899>Player information\n<#F8F9FA>Name: <#E8E8E8>%player_name%\n<#F8F9FA>Click to message"
    
  name-styles:
    default: "<#4A90E2>%player_name%"
    owner: "<bold><gradient:#FF0000:#FFAA00>%player_name%</gradient></bold>"
    admin: "<italic><color:#CC44FF>%player_name%</color></italic>"
```

![Configuration Image](https://cdn.modrinth.com/data/kcImu7Wi/images/154da5ba69d238aab11a09bb1c795b9e76e24edc.jpeg)

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/remchat channel <name>` | Switch between chat channels | `remmychat.use` |
| `/remchat reload` | Reload plugin configuration | `remmychat.admin` |
| `/msg <player> <message>` | Send private message | `remmychat.msg` |
| `/reply <message>` | Reply to last private message | `remmychat.msg` |
| `/msgtoggle` | Toggle receiving private messages | `remmychat.msgtoggle` |
| `/socialspy` | Monitor private messages between players | `remmychat.socialspy` |

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `remmychat.use` | Basic plugin access | `true` |
| `remmychat.msg` | Send private messages | `true` |
| `remmychat.msgtoggle` | Toggle private messages | `true` |
| `remmychat.msgtoggle.bypass` | Bypass message toggle | `op` |
| `remmychat.socialspy` | Use social spy feature | `op` |
| `remmychat.admin` | Administrative access | `op` |
| `remmychat.channel.<name>` | Access to specific channel | Varies |

## Integration

### PlaceholderAPI

RemmyChat automatically integrates with PlaceholderAPI if installed, allowing you to use any placeholders in your chat formats.

### Message Placeholder Parsing

RemmyChat can optionally parse placeholders within chat messages themselves. This feature provides two separate configuration options for maximum control:

#### Custom Placeholders in Messages
When enabled (`features.parse-placeholders-in-messages: true`), players can use custom placeholders directly in their chat messages:

**Example:**
```yaml
# In config.yml
placeholders:
  heart: "<red>❤</red>"
  gg: "<gradient:green:lime><bold>GG!</bold></gradient>"
  shrug: "<gray>¯\\_(ツ)_/¯</gray>"
```

Players can then type: `Great game everyone! %gg% %heart%`
Which renders as: `Great game everyone! GG! ❤`

#### PlaceholderAPI in Messages
When enabled (`features.parse-papi-placeholders-in-messages: true`), players can use PlaceholderAPI placeholders in their messages:

Players can type: `My balance is %vault_eco_balance% and I'm %player_name%`
Which renders as: `My balance is $1,250.50 and I'm Steve`

**Configuration:**
```yaml
features:
  parse-placeholders-in-messages: false      # Custom placeholders only
  parse-papi-placeholders-in-messages: false # PlaceholderAPI placeholders
```

**Security Notes:** 
- Custom placeholder parsing is generally safe as you control the definitions
- PAPI placeholder parsing gives players access to ALL PlaceholderAPI placeholders
- Consider carefully which information you want players to be able to display
- Both features are disabled by default for security reasons

### LuckPerms

When LuckPerms is detected, RemmyChat can use permission groups for chat formatting, simplifying setup for servers with existing permission structures.

## For Developers

### Building with Gradle

This project uses Gradle for building and requires Java 21 or higher.

To build:

```sh
./gradlew build
```

The output JAR will be in the `build/libs` directory.

## Support & Development

- **Documentation**: [remmychat.noximity.com](https://remmychat.noximity.com)
- **Website**: [noximity.com](https://noximity.com)
- **Issues & Feature Requests**: Please use our GitHub issues tracker
- **Version**: 1.4.6
- **License**: GPL-3.0