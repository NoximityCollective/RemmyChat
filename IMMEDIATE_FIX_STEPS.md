# 🚨 IMMEDIATE FIX: Discord Channels Not Working

**Problem:** Secondary channels (trade, staff, help) sending all messages to "general" channel instead of their configured channels.

## ⚡ Quick Fix (2 minutes)

### Step 1: Check What's Wrong
Run this command in-game or console:
```
/remmychat discord validate
```

You'll see something like:
```
❌ trade -> marketplace (NOT FOUND)
❌ help -> support (NOT FOUND)  
❌ event -> announcements (NOT FOUND)
✅ global -> general (FOUND)
```

### Step 2: Auto-Fix Your Configuration
```
/remmychat discord fix
```

This creates a `discord-corrected.yml` file with proper channel mappings.

### Step 3: Apply the Fix
1. Stop your server
2. Backup your current `discord.yml`
3. Replace `discord.yml` with `discord-corrected.yml`
4. Start your server
5. Run `/remmychat discord reload`

### Step 4: Test It Works
```
/remmychat discord test trade
/remmychat discord test staff
```

## 🎯 What Was Wrong

Your `discord.yml` maps channels to Discord channel names that don't exist:

```yaml
# YOUR CURRENT CONFIG (BROKEN)
trade:
  discord-channel: "marketplace"  # ❌ This channel doesn't exist
help:
  discord-channel: "support"      # ❌ This channel doesn't exist
event:
  discord-channel: "announcements" # ❌ This channel doesn't exist
```

## ✅ Manual Fix Alternative

If auto-fix doesn't work, manually edit `discord.yml`:

1. Check your actual Discord channel names
2. Update the mappings:

```yaml
# CORRECTED CONFIG
channels:
  trade:
    discord-channel: "trade"        # Use actual channel name
  help:
    discord-channel: "help"         # Use actual channel name  
  staff:
    discord-channel: "staff"        # Use actual channel name
```

3. Save and run `/remmychat discord reload`

## 🔍 Verify Fix Worked

After applying the fix:

```bash
/remmychat discord validate
```

Should show all ✅:
```
✅ trade -> trade (FOUND)
✅ help -> help (FOUND)
✅ staff -> staff (FOUND)
✅ global -> general (FOUND)
```

## 💡 Why This Happened

RemmyChat was trying to find Discord channels with names from your config file, but those channel names didn't exist in your actual Discord server. The fix updates the channel mappings to match your real Discord channels.

## 🆘 Still Not Working?

Run diagnostics for detailed info:
```
/remmychat discord diagnostics
```

This shows:
- Your actual Discord channels
- Current mappings
- What's working/broken
- Specific suggestions

---

**The fix is now complete!** Your secondary channels should work properly.