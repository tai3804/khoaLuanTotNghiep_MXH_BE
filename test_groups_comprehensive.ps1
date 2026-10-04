# Test Suite: Comprehensive Community Groups Testing (All Roles & Features)
$baseUrl = "http://localhost:8080/api/v1"
$results = @()

function Record-Test($name, $passed, $details) {
    $script:results += [PSCustomObject]@{
        TestName = $name
        Status   = if ($passed) { "PASS" } else { "FAIL" }
        Details  = $details
    }
    $color = if ($passed) { "Green" } else { "Red" }
    Write-Host "[$($results[-1].Status)] $name - $details" -ForegroundColor $color
}

function Login-User($email, $password) {
    $body = @{ email = $email; password = $password; deviceFingerprint = "test-fp-" + $email } | ConvertTo-Json
    try {
        $res = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method POST -Body $body -ContentType "application/json"
        return @{
            Token  = $res.data.accessToken
            UserId = $res.data.user.id
            Email  = $email
        }
    } catch {
        Write-Host "Login failed for $email : $($_.Exception.Message)" -ForegroundColor Red
        return $null
    }
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   BAT DAU KIEM THU TOAN DIEN TINH NANG NHOM (ALL ROLES)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Dang nhap cac tai khoan theo tung role
$userAdmin = Login-User "testuser@gmail.com" "admin123"      # Creator / Admin
$userMod   = Login-User "an.nguyen@example.com" "admin123"   # Duoc thang cap lam Moderator
$userMem   = Login-User "binh.tran@example.com" "admin123"   # Member (test join & pending)
$userGuest = Login-User "cuong.le@example.com" "admin123"    # Guest / Non-member

if (-not $userAdmin -or -not $userMod -or -not $userMem -or -not $userGuest) {
    Write-Host "Khong the dang nhap du 4 tai khoan de test!" -ForegroundColor Red
    exit 1
}

$headerAdmin = @{ Authorization = "Bearer $($userAdmin.Token)" }
$headerMod   = @{ Authorization = "Bearer $($userMod.Token)" }
$headerMem   = @{ Authorization = "Bearer $($userMem.Token)" }
$headerGuest = @{ Authorization = "Bearer $($userGuest.Token)" }

# -------------------------------------------------------------------------
# TEST 1: Get All Groups (Lay danh sach nhom & de xuat)
# -------------------------------------------------------------------------
try {
    $groupsRes = Invoke-RestMethod -Uri "$baseUrl/groups" -Method GET -Headers $headerAdmin
    $passed = ($groupsRes.code -eq 200 -and $groupsRes.data -is [System.Array])
    Record-Test "TC01: Get All Groups" $passed "Lay thanh cong $($groupsRes.data.Count) nhom tu backend database."
} catch {
    Record-Test "TC01: Get All Groups" $false $_.Exception.Message
}

# -------------------------------------------------------------------------
# TEST 2: Create Public Group (Tao nhom Cong khai - Role: Creator/Admin)
# -------------------------------------------------------------------------
$publicGroupId = $null
try {
    $randNum = Get-Random
    $createPublicBody = @{
        name        = "Nhom Cong Khai Test KLTN $randNum"
        description = "Mo ta nhom cong khai kiem thu"
        privacy     = "PUBLIC"
        coverUrl    = "https://images.unsplash.com/photo-1522071820081-009f0129c71c"
    } | ConvertTo-Json

    $res = Invoke-RestMethod -Uri "$baseUrl/groups" -Method POST -Body $createPublicBody -Headers $headerAdmin -ContentType "application/json"
    $publicGroupId = $res.data.id
    $isOwnerAdmin = ($res.data.admin -eq $true -and $res.data.member -eq $true)
    Record-Test "TC02: Create Public Group" ($res.code -in 200, 201 -and $publicGroupId -ne $null -and $isOwnerAdmin) "Tao nhom Public thanh cong (ID: $publicGroupId), Creator tu dong la Admin."
} catch {
    Record-Test "TC02: Create Public Group" $false $_.Exception.Message
}

# -------------------------------------------------------------------------
# TEST 3: Create Private Group (Tao nhom Rieng tu - Role: Creator/Admin)
# -------------------------------------------------------------------------
$privateGroupId = $null
try {
    $randNumPriv = Get-Random
    $createPrivateBody = @{
        name                 = "Nhom Rieng Tu Test KLTN $randNumPriv"
        description          = "Mo ta nhom rieng tu kiem thu"
        privacy              = "PRIVATE"
        postApprovalRequired = $true
        rules                = "Noi quy 1: Ton trong thanh vien`nNoi quy 2: Khong spam"
    } | ConvertTo-Json

    $res = Invoke-RestMethod -Uri "$baseUrl/groups" -Method POST -Body $createPrivateBody -Headers $headerAdmin -ContentType "application/json"
    $privateGroupId = $res.data.id
    Record-Test "TC03: Create Private Group" ($res.code -in 200, 201 -and $privateGroupId -ne $null -and $res.data.privacy -eq "PRIVATE") "Tao nhom Private thanh cong (ID: $privateGroupId) kem noi quy va duyet bai."
} catch {
    Record-Test "TC03: Create Private Group" $false $_.Exception.Message
}

# -------------------------------------------------------------------------
# TEST 4: Get Group Detail (Lay chi tiet nhom)
# -------------------------------------------------------------------------
try {
    $detailRes = Invoke-RestMethod -Uri "$baseUrl/groups/$publicGroupId" -Method GET -Headers $headerAdmin
    $passed = ($detailRes.data.id -eq $publicGroupId -and $detailRes.data.memberCount -ge 1)
    Record-Test "TC04: Get Group Detail" $passed "Lay chi tiet nhom thanh cong, Ten: $($detailRes.data.name)."
} catch {
    Record-Test "TC04: Get Group Detail" $false $_.Exception.Message
}

# -------------------------------------------------------------------------
# TEST 5: Update Group Info (Admin cap nhat) vs Non-Admin bi chan
# -------------------------------------------------------------------------
try {
    # 5.1 Admin cap nhat
    $updateBody = @{
        name        = "Ten Nhom Da Cap Nhat Moi"
        description = "Mo ta da duoc sua boi Admin"
        rules       = "Noi quy cap nhat"
    } | ConvertTo-Json

    $updateRes = Invoke-RestMethod -Uri "$baseUrl/groups/$publicGroupId" -Method PUT -Body $updateBody -Headers $headerAdmin -ContentType "application/json"
    $adminPassed = ($updateRes.data.name -eq "Ten Nhom Da Cap Nhat Moi")
    
    # 5.2 Non-member (UserGuest) co tinh cap nhat -> Phai bi tu choi 401/403
    $nonAdminRejected = $false
    try {
        Invoke-RestMethod -Uri "$baseUrl/groups/$publicGroupId" -Method PUT -Body $updateBody -Headers $headerGuest -ContentType "application/json"
    } catch {
        $nonAdminRejected = $true
    }

    Record-Test "TC05: Update Group Settings" ($adminPassed -and $nonAdminRejected) "Admin cap nhat thanh cong; Nguoi ngoai bi chan khong co quyen (403 Forbidden)."
} catch {
    Record-Test "TC05: Update Group Settings" $false $_.Exception.Message
}

# -------------------------------------------------------------------------
# TEST 6: Join Public Group (User 2 tham gia nhom Public -> APPROVED ngay)
# -------------------------------------------------------------------------
try {
    $joinRes = Invoke-RestMethod -Uri "$baseUrl/groups/$publicGroupId/join" -Method POST -Headers $headerMod
    $passed = ($joinRes.data.member -eq $true -and $joinRes.data.joinStatus -eq "APPROVED" -and $joinRes.data.memberCount -ge 2)
    Record-Test "TC06: Join Public Group" $passed "User 2 tham gia nhom Public thanh cong, trang thai APPROVED ngay lap tuc."
} catch {
    Record-Test "TC06: Join Public Group" $false $_.Exception.Message
}

# -------------------------------------------------------------------------
# TEST 7: Join Private Group (User 3 xin vao nhom Private -> PENDING)
# -------------------------------------------------------------------------
try {
    $joinPrivRes = Invoke-RestMethod -Uri "$baseUrl/groups/$privateGroupId/join" -Method POST -Headers $headerMem
    $passed = ($joinPrivRes.data.joinStatus -eq "PENDING")
    Record-Test "TC07: Join Private Group (Pending)" $passed "User 3 xin vao nhom Private thanh cong, trang thai PENDING cho duyet."
} catch {
    Record-Test "TC07: Join Private Group (Pending)" $false $_.Exception.Message
}

# -------------------------------------------------------------------------
# TEST 8: Get Group Members & Pending Requests (Xem thanh vien & hang cho)
# -------------------------------------------------------------------------
try {
    $membersRes = Invoke-RestMethod -Uri "$baseUrl/groups/$privateGroupId/members?includePending=true" -Method GET -Headers $headerAdmin
    $pendingMember = $membersRes.data | Where-Object { $_.userId -eq $userMem.UserId }
    $passed = ($pendingMember -ne $null -and $pendingMember.status -eq "PENDING")
    Record-Test "TC08: Get Members with Pending" $passed "Admin thay User 3 nam trong hang cho PENDING cua nhom Private."
} catch {
    Record-Test "TC08: Get Members with Pending" $false $_.Exception.Message
}

# -------------------------------------------------------------------------
# TEST 9: Review Member (Duyet thanh vien vao nhom Private - Approve)
# -------------------------------------------------------------------------
try {
    $reviewRes = Invoke-RestMethod -Uri "$baseUrl/groups/$privateGroupId/members/$($userMem.UserId)/review?approved=true" -Method POST -Headers $headerAdmin
    $checkRes = Invoke-RestMethod -Uri "$baseUrl/groups/$privateGroupId/members" -Method GET -Headers $headerAdmin
    $approvedMember = $checkRes.data | Where-Object { $_.userId -eq $userMem.UserId }
    $passed = ($approvedMember -ne $null -and $approvedMember.status -eq "APPROVED")
    Record-Test "TC09: Review Member (Approve)" $passed "Admin duyet User 3 vao nhom Private thanh cong (Status: APPROVED)."
} catch {
    Record-Test "TC09: Review Member (Approve)" $false $_.Exception.Message
}

# -------------------------------------------------------------------------
# TEST 10: Promote Member to MODERATOR (Phan quyen Kiem duyet vien)
# -------------------------------------------------------------------------
try {
    # 10.1 Admin thang cap User 2 len MODERATOR
    Invoke-RestMethod -Uri "$baseUrl/groups/$publicGroupId/members/$($userMod.UserId)/role?role=MODERATOR" -Method PUT -Headers $headerAdmin
    $membersList = Invoke-RestMethod -Uri "$baseUrl/groups/$publicGroupId/members" -Method GET -Headers $headerAdmin
    $user2Role = ($membersList.data | Where-Object { $_.userId -eq $userMod.UserId }).role
    $promoted = ($user2Role -eq "MODERATOR")

    # 10.2 User thuong (User 3) co tinh doi role -> Phai bi tu choi
    $memberForbidden = $false
    try {
        Invoke-RestMethod -Uri "$baseUrl/groups/$publicGroupId/members/$($userMod.UserId)/role?role=ADMIN" -Method PUT -Headers $headerMem
    } catch {
        $memberForbidden = $true
    }

    Record-Test "TC10: Role Promotion to MODERATOR" ($promoted -and $memberForbidden) "Admin thang cap User 2 len MODERATOR thanh cong; Thanh vien thuong bi chan khong duoc doi role."
} catch {
    Record-Test "TC10: Role Promotion to MODERATOR" $false $_.Exception.Message
}

# -------------------------------------------------------------------------
# TEST 11: Add Members Directly (Moi / them truc tiep thanh vien vao nhom)
# -------------------------------------------------------------------------
try {
    $addBody = "[ `"$($userGuest.UserId)`" ]"
    $addRes = Invoke-RestMethod -Uri "$baseUrl/groups/$publicGroupId/members" -Method POST -Body $addBody -Headers $headerAdmin -ContentType "application/json"
    $checkGuest = Invoke-RestMethod -Uri "$baseUrl/groups/$publicGroupId/members" -Method GET -Headers $headerAdmin
    $guestMember = $checkGuest.data | Where-Object { $_.userId -eq $userGuest.UserId }
    $passed = ($guestMember -ne $null -and $guestMember.status -eq "APPROVED")
    Record-Test "TC11: Add Member Directly" $passed "Admin them User 4 truc tiep vao nhom thanh cong."
} catch {
    Record-Test "TC11: Add Member Directly" $false $_.Exception.Message
}

# -------------------------------------------------------------------------
# TEST 12: Moderator Rights (Mod kick thanh vien, khong kick duoc Creator)
# -------------------------------------------------------------------------
try {
    # 12.1 Mod kick User 4 khoi nhom
    Invoke-RestMethod -Uri "$baseUrl/groups/$publicGroupId/members/$($userGuest.UserId)" -Method DELETE -Headers $headerMod
    $checkAfterKick = Invoke-RestMethod -Uri "$baseUrl/groups/$publicGroupId/members" -Method GET -Headers $headerAdmin
    $isKicked = (($checkAfterKick.data | Where-Object { $_.userId -eq $userGuest.UserId }) -eq $null)

    # 12.2 Mod co tinh kick Creator (UserAdmin) -> Phai bi chan
    $cantKickOwner = $false
    try {
        Invoke-RestMethod -Uri "$baseUrl/groups/$publicGroupId/members/$($userAdmin.UserId)" -Method DELETE -Headers $headerMod
    } catch {
        $cantKickOwner = $true
    }

    Record-Test "TC12: Moderator Rights Enforcement" ($isKicked -and $cantKickOwner) "Moderator kick duoc thanh vien vi pham; He thong bao ve Creator khong the bi kick boi Mod."
} catch {
    Record-Test "TC12: Moderator Rights Enforcement" $false $_.Exception.Message
}

# -------------------------------------------------------------------------
# TEST 13: Post In Group (Dang bai vao nhom - Kiem tra quyen thanh vien)
# -------------------------------------------------------------------------
try {
    # 13.1 Thanh vien (User 2) dang bai vao nhom
    $resRaw = & curl.exe -s -X POST "$baseUrl/posts" `
        -H "Authorization: Bearer $($userMod.Token)" `
        -F "content=Bai viet kiem thu dang vao nhom tu User 2 (Member/Mod)" `
        -F "privacy=PUBLIC" `
        -F "groupId=$publicGroupId"
    $postRes = $resRaw | ConvertFrom-Json
    $memberPostOk = ($postRes.code -in 200, 201 -and $postRes.data.id -ne $null)

    # 13.2 Nguoi ngoai (User 4 da bi kick) co tinh dang bai vao nhom -> Phai bi chan (401 / 403)
    $guestResRaw = & curl.exe -s -w "\nHTTP_STATUS:%{http_code}" -X POST "$baseUrl/posts" `
        -H "Authorization: Bearer $($userGuest.Token)" `
        -F "content=Bai viet tu nguoi ngoai nhom" `
        -F "privacy=PUBLIC" `
        -F "groupId=$publicGroupId"
    $nonMemberBlocked = ($guestResRaw -match "401" -or $guestResRaw -match "403")

    Record-Test "TC13: Post In Group Permission" ($memberPostOk -and $nonMemberBlocked) "Thanh vien nhom dang bai thanh cong; Nguoi ngoai bi chan khong duoc dang bai (401/403)."
} catch {
    Record-Test "TC13: Post In Group Permission" $false $_.Exception.Message
}

# -------------------------------------------------------------------------
# TEST 14: Get Group Posts Feed (Lay danh sach bai viet trong nhom)
# -------------------------------------------------------------------------
try {
    $feedRes = Invoke-RestMethod -Uri "$baseUrl/posts/group/$publicGroupId" -Method GET -Headers $headerAdmin
    $postsCount = if ($feedRes.data.content) { $feedRes.data.content.Count } elseif ($feedRes.data) { $feedRes.data.Count } else { 0 }
    $passed = ($feedRes.code -eq 200 -and $postsCount -ge 1)
    Record-Test "TC14: Get Group Posts Feed" $passed "Lay thanh cong $postsCount bai viet thuoc nhom."
} catch {
    Record-Test "TC14: Get Group Posts Feed" $false $_.Exception.Message
}

# -------------------------------------------------------------------------
# TEST 15: Leave Group (Roi nhom)
# -------------------------------------------------------------------------
try {
    # 15.1 User 2 roi nhom
    Invoke-RestMethod -Uri "$baseUrl/groups/$publicGroupId/members/me" -Method DELETE -Headers $headerMod
    $checkAfterLeave = Invoke-RestMethod -Uri "$baseUrl/groups/$publicGroupId/members" -Method GET -Headers $headerAdmin
    $user2Left = (($checkAfterLeave.data | Where-Object { $_.userId -eq $userMod.UserId }) -eq $null)

    # 15.2 Creator co tinh roi nhom cua minh -> Phai bi chan
    $ownerCantLeave = $false
    try {
        Invoke-RestMethod -Uri "$baseUrl/groups/$publicGroupId/members/me" -Method DELETE -Headers $headerAdmin
    } catch {
        $ownerCantLeave = $true
    }

    Record-Test "TC15: Leave Group Enforcement" ($user2Left -and $ownerCantLeave) "Thanh vien roi nhom thanh cong; Creator bi chan khong duoc tu roi nhom (phai xoa nhom)."
} catch {
    Record-Test "TC15: Leave Group Enforcement" $false $_.Exception.Message
}

# -------------------------------------------------------------------------
# TEST 16: Delete Group (Xoa nhom)
# -------------------------------------------------------------------------
try {
    # 16.1 Non-admin (User 3) co tinh xoa nhom -> Bi tu choi
    $nonAdminCantDelete = $false
    try {
        Invoke-RestMethod -Uri "$baseUrl/groups/$publicGroupId" -Method DELETE -Headers $headerMem
    } catch {
        $nonAdminCantDelete = $true
    }

    # 16.2 Creator/Admin xoa nhom
    $delRes = Invoke-RestMethod -Uri "$baseUrl/groups/$publicGroupId" -Method DELETE -Headers $headerAdmin
    $adminDeleted = ($delRes.code -eq 200)

    # 16.3 Don dep nhom Private test
    Invoke-RestMethod -Uri "$baseUrl/groups/$privateGroupId" -Method DELETE -Headers $headerAdmin | Out-Null

    Record-Test "TC16: Delete Group" ($nonAdminCantDelete -and $adminDeleted) "Thanh vien khong the xoa nhom; Creator xoa nhom thanh cong."
} catch {
    Record-Test "TC16: Delete Group" $false $_.Exception.Message
}

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "                TONG KET KET QUA KIEM THU" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
$passCount = ($results | Where-Object { $_.Status -eq "PASS" }).Count
$totalCount = $results.Count
Write-Host "Tong so ca kiem thu: $totalCount" -ForegroundColor White
Write-Host "So ca THANH CONG:   $passCount" -ForegroundColor Green
Write-Host "So ca THAT BAI:     $($totalCount - $passCount)" -ForegroundColor $(if ($totalCount -eq $passCount) { "Green" } else { "Red" })

$results | Format-Table -AutoSize
