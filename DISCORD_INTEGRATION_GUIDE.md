# RemmyChat Discord Integration Guide

This guide will help you set up and troubleshoot Discord integration with RemmyChat.

## 🚨 Problem Summary

If your secondary channels (like "trade", "staff", "help") are not working and all messages are going to the "general" channel, this is a **channel mapping issue**.

## 🔧 Quick Fix Steps

### 1. Check Your Current Setup

Run this command in-game or console:
```
/remmychat discord diagnostics
```

This will show you:
- Available Discord channels in your server
- Your current channel mappings
- Which mappings are working/broken

### 2. Validate Your Channels

```
/remmychat discord validate
```

This will check each channel mapping and tell you which Discord channels don't exist.

### 3. Test Individual Channels

```
/remmychat discord test trade
```

Replace "trade" with any channel name. This sends a test message to verify the channel works.

### 4. Auto-Fix Configuration

```
/remmychat discord fix
```

This generates a corrected `discord-corrected.yml` file with proper channel mappings based on your actual Discord server.

### 5. Get Configuration Help

```
/remmychat discord configure
```

This analyzes your setup and provides specific suggestions.

## 🎯 Root Cause

The issue was that RemmyChat's Discord integration was using DiscordSRV's channel mapping system, but your `discord.yml` contains custom channel mappings that DiscordSRV doesn't know about.

**Example of the problem:**
- RemmyChat maps "trade" → "marketplace" (from discord.yml)
- But your Discord server doesn't have a channel named "marketplace"
- DiscordSRV can't find "marketplace", so it fails
- Messages fall back to "general" channel

## 📝 Configuration File Explained

Your `discord.yml` file contains these mappings:

```yaml
channels:
  global:
    discord-channel: "general"     # ✅ This works (channel exists)
  
  staff:
    discord-channel: "staff"       # ❓ Check if "staff" channel exists
  
  trade:
    discord-channel: "marketplace" # ❌ Likely doesn't exist
  
  help:
    discord-channel: "support"     # ❌ Likely doesn't exist
  
  event:
    discord-channel: "announcements" # ❌ Likely doesn't exist
```

## 🛠️ How to Fix Channel Mappings

### Method 1: Update discord.yml Manually

1. Check your actual Discord channel names
2. Edit `discord.yml` to match:

```yaml
channels:
  trade:
    discord-channel: "trade"        # Change to actual channel name
  
  help:
    discord-channel: "help"         # Change to actual channel name
  
  event:
    discord-channel: "events"       # Change to actual channel name
```

### Method 2: Use Auto-Fix Tool

1. Run `/remmychat discord fix`
2. Review the generated `discord-corrected.yml`
3. Replace your `discord.yml` with the corrected version
4. Run `/remmychat discord reload`

## 🔍 New Features Added

### Enhanced Discord Commands

- `/remmychat discord validate` - Check all channel mappings
- `/remmychat discord diagnostics` - Full diagnostic information
- `/remmychat discord test <channel>` - Test individual channels
- `/remmychat discord configure` - Get configuration suggestions
- `/remmychat discord fix` - Auto-generate corrected config

### Improved Channel Detection

- Direct JDA API integration (bypasses DiscordSRV limitations)
- Fallback to "general" channel when specific channels fail
- Case-insensitive channel matching
- Similar channel name suggestions

### Direction Support

The plugin now respects `direction` settings from `discord.yml`:

- `"both"` - Messages flow both ways (default)
- `"minecraft-to-discord"` - Only MC → Discord
- `"discord-to-minecraft"` - Only Discord → MC

### Better Error Handling

- Detailed logging when channels aren't found
- Suggestions for similar channel names
- Graceful fallback behavior

## 📋 Channel Setup Checklist

### ✅ Prerequisites
- [ ] DiscordSRV plugin installed and configured
- [ ] Discord bot has proper permissions
- [ ] Bot can see and send messages in your channels

### ✅ RemmyChat Configuration
- [ ] `discord.yml` has `integration.enabled: true`
- [ ] Channel mappings match your actual Discord channels
- [ ] Direction settings are correct for each channel
- [ ] No typos in channel names (case-sensitive!)

### ✅ Testing
- [ ] Run `/remmychat discord validate` - all channels show ✅
- [ ] Test each channel with `/remmychat discord test <channel>`
- [ ] Send test messages from Minecraft to Discord
- [ ] Send test messages from Discord to Minecraft

## 🚑 Troubleshooting Common Issues

### "All messages go to general channel"
- **Cause**: Channel mappings are incorrect
- **Fix**: Run `/remmychat discord validate` and fix mappings

### "Discord channel not found" errors
- **Cause**: Channel names in `discord.yml` don't match Discord server
- **Fix**: Check channel names with `/remmychat discord diagnostics`

### "Messages only go one way"
- **Cause**: Direction settings or DiscordSRV configuration
- **Fix**: Check `direction` settings in `discord.yml`

### "Bot doesn't respond"
- **Cause**: DiscordSRV not properly configured
- **Fix**: Configure DiscordSRV first, then RemmyChat

## 📖 Example Working Configuration

```yaml
# discord.yml - Working Example
integration:
  enabled: true
  mode: "bidirectional"

channels:
  global:
    enabled: true
    discord-channel: "general"    # Must match actual Discord channel
    direction: "both"

  staff:
    enabled: true
    discord-channel: "staff"      # Must exist in your Discord server
    direction: "both"
    allowed-roles:
      - "Staff"
      - "Admin"

  trade:
    enabled: true
    discord-channel: "trading"    # Use YOUR actual channel name
    direction: "both"

formatting:
  minecraft-to-discord:
    format: "**%player_name%**: %message%"
    channel-formats:
      global: "**%player_name%**: %message%"
      staff: "**[Staff]** %player_name%: %message%"
      trade: "**[Trade]** %player_name%: %message%"

  discord-to-minecraft:
    format: "<blue>[Discord]</blue> <gray>%username%</gray>: %message%"
    channel-formats:
      general: "<blue>[Discord]</blue> <gray>%username%</gray>: %message%"
      staff: "<gold>[Discord-Staff]</gold> <yellow>%username%</yellow>: %message%"
      trading: "<green>[Discord-Trade]</green> <gray>%username%</gray>: %message%"
```

## 🔧 Technical Details

### What Was Fixed

1. **Channel Resolution**: Now uses JDA API directly instead of DiscordSRV's channel mapping
2. **Fallback Behavior**: Falls back to "general" channel when specific channels fail
3. **Direction Support**: Respects `direction` settings from configuration
4. **Better Validation**: Comprehensive channel validation and testing tools
5. **Auto-Configuration**: Tools to generate correct configuration automatically

### Key Code Changes

- `DiscordSRVIntegration.kt`: Enhanced channel resolution with JDA API
- `DiscordConfigHelper.kt`: New configuration validation and auto-fix tools
- Enhanced commands with validation, testing, and diagnostic capabilities

## 🆘 Getting Help

If you're still having issues:

1. Run `/remmychat discord diagnostics` and share the output
2. Check your server console for error messages
3. Verify your Discord bot permissions
4. Make sure DiscordSRV is working independently first

## 📞 Support Commands Quick Reference

```bash
/remmychat discord                    # Show status
/remmychat discord validate           # Check all channels  
/remmychat discord diagnostics        # Full diagnostic info
/remmychat discord test <channel>     # Test specific channel
/remmychat discord configure          # Get setup suggestions
/remmychat discord fix               # Generate corrected config
/remmychat discord reload            # Reload after config changes
```

---

**Remember**: The channel names in your `discord.yml` file must **exactly match** the channel names in your Discord server (case-sensitive)!