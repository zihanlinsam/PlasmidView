with open("app/src/main/java/com/plasmidview/ui/map/MapScreenContent.kt", "r") as f:
    content = f.read()

# Replace any Icons.Default.* that aren't ContentCopy
import re
# Find all Icons.Default references
icons = set(re.findall(r'Icons\.Default\.(\w+)', content))
print("Icons used:", icons)

# Replace with Text emoji equivalents
content = content.replace('icon = { Icon(Icons.Default.RadioButtonUnchecked, null, Modifier.size(16.dp)) }',
                          'icon = { Icon(Icons.Default.List, null, Modifier.size(16.dp)) }')
content = content.replace('icon = { Icon(Icons.Default.Minimize, null, Modifier.size(16.dp)) }',
                          'icon = { Text("━", fontSize = 14.sp) }')

with open("app/src/main/java/com/plasmidview/ui/map/MapScreenContent.kt", "w") as f:
    f.write(content)
print("Fixed MapScreen icons")
