# Placeholder Functionality Test

## Test Setup

1. Enable placeholder parsing in `config.yml`:
```yaml
features:
  parse-placeholders-in-messages: true      # Custom placeholders
  parse-papi-placeholders-in-messages: true # PlaceholderAPI placeholders
```

2. Add test placeholders to `config.yml`:
```yaml
placeholders:
  heart: "<red>❤</red>"
  gg: "<gradient:green:lime><bold>GG!</bold></gradient>"
  star: "<yellow>⭐</yellow>"
  test: "This is a test placeholder"
```

## Test Cases

### Test 1: Custom Placeholders Only
**Config**: `parse-placeholders-in-messages: true`, `parse-papi-placeholders-in-messages: false`

**Input**: `"Great game! %gg% %heart%"`
**Expected**: `"Great game! GG! ❤"`

**Input**: `"Testing %test% with %star%"`
**Expected**: `"Testing This is a test placeholder with ⭐"`

### Test 2: PAPI Placeholders Only (if PlaceholderAPI installed)
**Config**: `parse-placeholders-in-messages: false`, `parse-papi-placeholders-in-messages: true`

**Input**: `"Hello %player_name% in %player_world%"`
**Expected**: `"Hello Steve in world"`

**Input**: `"My balance: %vault_eco_balance%"`
**Expected**: `"My balance: $1,250.50"`

### Test 3: Both Enabled
**Config**: `parse-placeholders-in-messages: true`, `parse-papi-placeholders-in-messages: true`

**Input**: `"I'm %player_name% and that was %gg% %heart%"`
**Expected**: `"I'm Steve and that was GG! ❤"`

### Test 4: Neither Enabled (Default behavior)
**Config**: `parse-placeholders-in-messages: false`, `parse-papi-placeholders-in-messages: false`

**Input**: `"This %gg% should %heart% not parse"`
**Expected**: `"This %gg% should %heart% not parse"`

### Test 5: Invalid Placeholders
**Input**: `"This %nonexistent% placeholder should stay"`
**Expected**: `"This %nonexistent% placeholder should stay"`

## Manual Testing Steps

1. Start server with RemmyChat
2. Set config values as specified
3. Run `/remmychat reload`
4. Type test messages in chat
5. Verify output matches expected results

## Code Verification

### Key Files to Check

1. **ConfigManager.kt** - Should have:
   - `isParsePlaceholdersInMessages` property
   - `isParsePapiPlaceholdersInMessages` property

2. **PlaceholderManager.kt** - Should have:
   - `applyCustomPlaceholders()` method
   - `applyPapiPlaceholders()` method
   - `applyAllPlaceholders()` method

3. **FormatService.kt** - Should have in `formatMessageContent()`:
   ```kotlin
   if (plugin.getConfigManager()?.isParsePlaceholdersInMessages == true) {
       message = plugin.getPlaceholderManager()?.applyCustomPlaceholders(message) ?: message
   }

   if (plugin.getConfigManager()?.isParsePapiPlaceholdersInMessages == true) {
       message = plugin.getPlaceholderManager()?.applyPapiPlaceholders(player, message) ?: message
   }
   ```

## Expected Behavior

- ✅ Custom placeholders parse when enabled
- ✅ PAPI placeholders parse when enabled (with PlaceholderAPI)
- ✅ Both work together when both enabled
- ✅ No parsing when disabled
- ✅ Invalid placeholders remain unchanged
- ✅ Works in all chat channels
- ✅ Works in private messages

## Debug Tips

If placeholders don't work:

1. Check config syntax is correct
2. Verify `/remmychat reload` was run
3. Check console for errors
4. Enable debug mode: `debug.enabled: true`
5. Test with simple placeholders first

## Performance Notes

- Custom placeholders are processed first
- PAPI placeholders are processed second
- Symbol replacement happens after placeholder parsing
- URL detection happens last