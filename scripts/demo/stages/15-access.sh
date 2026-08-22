#!/usr/bin/env bash
# 15-access — access scopes, a custom role, demo accounts, role assignments.
#
# Everything goes through /api/v1/access and /api/v1/users, so the audit trail and the guards see
# exactly what they would see if an administrator had clicked through the UI.

stage_banner "2. Phân quyền và tài khoản demo"

# ── system roles (seeded by V1/V6, looked up not created) ────────────────────
ROLES_JSON=$(call GET "/access/roles?page=0&size=100")
for rc in ADMIN MANAGER OPERATOR; do
    remember "roles.$rc" "$(echo "$ROLES_JSON" \
        | extract "next(r['roleId'] for r in d['result']['content'] if r['code']=='$rc')")"
done
info "vai trò hệ thống: ADMIN, MANAGER, OPERATOR"

# ── custom role ──────────────────────────────────────────────────────────────
# companyId omitted -> a system-wide role, which is what the plant-scoped assignments below need.
log "Tạo vai trò tuỳ chỉnh $ROLE_PLANNER"
remember "roles.$ROLE_PLANNER" "$(call POST /access/roles \
    "{\"code\":\"$ROLE_PLANNER\",\"name\":\"$ROLE_PLANNER_NAME\",\"description\":\"Vai trò demo: chạy MRP, duyệt đề xuất cung ứng, đọc dữ liệu sản xuất\"}" \
    | rid roleId)"

PERMS_JSON=$(call GET "/access/permissions?page=0&size=100")
for pcode in $PLANNER_PERMISSIONS; do
    pid=$(echo "$PERMS_JSON" \
        | extract "next((p['permissionId'] for p in d['result']['content'] if p['code']=='$pcode'), '')")
    if [ -z "$pid" ]; then
        warn "bỏ qua quyền $pcode — không có trong danh mục"
        continue
    fi
    call POST "/access/roles/${ID[roles.$ROLE_PLANNER]}/permissions/$pid" >/dev/null
    info "cấp $pcode cho $ROLE_PLANNER"
done

# ── scopes ───────────────────────────────────────────────────────────────────
# A scope is a named set of resources; the guard resolves a request's company/plant/warehouse
# against it. COMPANY cascades down to its plants and warehouses; PLANT does not leak sideways.
log "Tạo phạm vi truy cập"
while IFS='|' read -r scode sname stype; do
    [ -z "$scode" ] && continue
    sid=$(call POST /access/scopes \
        "{\"code\":\"$scode\",\"name\":\"$sname\",\"scopeType\":\"$stype\",\"description\":\"Phạm vi dữ liệu demo\"}" \
        | rid scopeId)
    remember "scopes.$scode" "$sid"
    info "phạm vi $scode ($stype)"
done <<EOF
$SCOPE_ALL|$SCOPE_ALL_NAME|COMPANY
$SCOPE_HN|$SCOPE_HN_NAME|PLANT
$SCOPE_SG|$SCOPE_SG_NAME|PLANT
$SCOPE_TP|$SCOPE_TP_NAME|WAREHOUSE_GROUP
EOF

add_resource() {  # add_resource SCOPE_CODE RESOURCE_TYPE RESOURCE_ID
    call POST "/access/scopes/${ID[scopes.$1]}/resources" \
        "{\"resourceType\":\"$2\",\"resourceId\":\"$3\"}" >/dev/null
}

add_resource "$SCOPE_ALL" COMPANY "${ID[company.id]}"

# Plant scopes list the plant AND each of its warehouses, so warehouse-scoped guards
# (inventory receive/issue, item-warehouse settings) resolve for these users too — same shape
# dev-seed.sql uses, expressed through the API.
for pc in "$P1_CODE:$SCOPE_HN" "$P2_CODE:$SCOPE_SG"; do
    plant="${pc%%:*}"; scope="${pc#*:}"
    add_resource "$scope" PLANT "${ID[plants.$plant.id]}"
    for wh in "$WH_NVL" "$WH_BTP" "$WH_TP"; do
        add_resource "$scope" WAREHOUSE "${ID[plants.$plant.warehouses.$wh]}"
    done
done
add_resource "$SCOPE_TP" WAREHOUSE "${ID[plants.$P1_CODE.warehouses.$WH_TP]}"
info "đã gán tài nguyên cho 4 phạm vi"

# ── accounts ─────────────────────────────────────────────────────────────────
log "Tạo tài khoản demo (mật khẩu chung: $DEMO_PASSWORD)"
while IFS='|' read -r uname email rolecode scopecode expdays; do
    [ -z "$uname" ] && continue
    slug=""
    uid=$(call POST /users \
        "{\"username\":\"$uname\",\"email\":\"$email\",\"password\":\"$DEMO_PASSWORD\"}" | rid userId)
    # Manifest keys nest on '.', and usernames contain one — keyed verbatim, `quanly.hanoi` would
    # come out as users -> quanly -> hanoi. Slug the key and carry the real username as a value.
    slug="${uname//./_}"
    remember "users.$slug.id" "$uid"
    note     "users.$slug.username" "$uname"
    note     "users.$slug.role" "$rolecode"
    note     "users.$slug.scope" "$scopecode"

    # An assignment that expires gives the RBAC admin screen a non-null expiresAt to render — the
    # field FE reported as missing in ADMIN_RBAC_BACKEND_ASSIGNMENT_EXPIRY_GAP_2026-08-13.md.
    if [ "$expdays" = "-" ]; then
        body="{\"userId\":\"$uid\",\"roleId\":\"${ID[roles.$rolecode]}\",\"scopeId\":\"${ID[scopes.$scopecode]}\"}"
        info "$uname -> $rolecode @ $scopecode"
    else
        body="{\"userId\":\"$uid\",\"roleId\":\"${ID[roles.$rolecode]}\",\"scopeId\":\"${ID[scopes.$scopecode]}\",\"expiresAt\":\"$(d_plus "$expdays")T00:00:00Z\"}"
        info "$uname -> $rolecode @ $scopecode (hết hạn sau $expdays ngày)"
    fi
    call POST /access/assignments "$body" >/dev/null
done <<EOF
$(echo "$USER_ROWS" | sed '/^[[:space:]]*$/d')
EOF

# 🔴 Known limitation, deliberately NOT worked around here — write it into the manifest so the
# person demoing does not meet it live: PermissionGuard.hasPermission(...) only resolves
# assignments whose scope type is GLOBAL. Endpoints guarded that way (GET /uoms, GET /companies)
# therefore return 403 for every plant-scoped account below, even though the permission is granted.
# Use `admin` for global master-data screens. See module/uom/CLAUDE.md.
note "notes.globalScopeLimitation" "Tài khoản gán phạm vi PLANT/COMPANY vẫn bị 403 ở các endpoint dùng hasPermission (GET /uoms, GET /companies) vì guard chỉ đọc assignment scope GLOBAL. Dùng tài khoản admin cho các màn hình master data toàn cục."
