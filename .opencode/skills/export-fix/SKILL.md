---
name: export-fix
description: Diagnose and fix DOCX/PDF export issues
---

## Steps

### 1. Identify the failure
| Symptom | Likely cause |
|---------|-------------|
| DOCX: wrong formatting | Apache POI rendering mismatch |
| DOCX: missing content | TipTap JSON node not handled in walker |
| DOCX: corrupt file | XML structure invalid |
| PDF: blank output | LibreOffice conversion failed |
| PDF: missing fonts | Font not installed in container |
| PDF: wrong layout | HTML conversion issue |

### 2. Check TipTap JSON structure
```bash
# Extract document from MongoDB to inspect
mongosh --eval 'db.documents.findOne({_id: "..."})'
```
- Verify node types match what exporter expects
- Check for unsupported custom nodes

### 3. Debug DOCX export
- Add missing node type in `TipTapTreeWalker.java` switch statement
- Common missing: `table`, `bullet_list`, `ordered_list`, `horizontal_rule`
- For inline formatting: check `marks` array on text nodes

### 4. Debug PDF export
- PDF uses DOCX → LibreOffice pipeline
- Fix the DOCX first, then test PDF
- If LibreOffice fails: check headless mode, timeout (30s), temp file permissions

### 5. Test fix
```bash
# Backend test
cd backend && ./gradlew test --tests "*DocxExporter*"

# Manual verification
curl -o test.docx http://localhost:8080/api/documents/{id}/export/docx
curl -o test.pdf http://localhost:8080/api/documents/{id}/export/pdf
```
