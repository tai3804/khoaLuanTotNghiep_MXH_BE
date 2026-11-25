---
title: Tài khoản và bảo mật
slug: tai-khoan-va-bao-mat
audience: user
tags: [đăng ký, đăng nhập, đăng xuất, mật khẩu, quên mật khẩu, OTP, xác thực hai bước, MFA, 2FA, TOTP, Authenticator, thiết bị đăng nhập, tài khoản bị khóa, bị cấm, tạo tài khoản]
updated: 2026-11-19
---

# Tài khoản và bảo mật

Tài liệu này hướng dẫn người dùng KLTN Social tạo tài khoản bằng email và mã OTP, đăng nhập, đăng xuất, lấy lại mật khẩu, bật xác thực hai bước (MFA, 2FA), quản lý các thiết bị đang đăng nhập, và giải thích các giới hạn bảo mật (nhập sai, gửi lại mã, tần suất yêu cầu) cùng các trạng thái tài khoản.

## Cách đăng ký tài khoản bằng email và mã OTP

Muốn tạo tài khoản mới (đăng ký, sign up), bạn cần một địa chỉ email còn dùng được để nhận mã OTP gồm 6 chữ số. Đăng ký có 3 bước, thanh tiến trình hiển thị "Email", "Mã OTP", "Thông tin". Trên trang đăng nhập hãy chọn thẻ "Đăng ký" (hoặc liên kết "Đăng ký tài khoản mới"):

1. Bước Email: nhập "Địa chỉ Email đăng ký" rồi bấm "Tiếp Tục & Gửi Mã OTP". Hệ thống chuyển email về chữ thường và kiểm tra email chưa được dùng.
2. Bước Mã OTP: mở hộp thư, tìm thư có tiêu đề "Mã xác thực đăng ký tài khoản", nhập mã 6 chữ số vào ô "Nhập mã xác thực OTP (6 chữ số)" rồi bấm "Xác Thực OTP & Tiếp Tục". Nhập nhầm email thì bấm "Sửa". Nút "Gửi lại mã OTP" chỉ dùng được sau khi đếm ngược "Gửi lại sau (60s)" kết thúc.
3. Bước Thông tin: nhập "Họ & Tên đệm", "Tên chính", "Ngày sinh", "Giới tính" (Nam, Nữ, Khác), "Mật khẩu đăng nhập", "Xác nhận mật khẩu" rồi bấm "Hoàn Tất Đăng Ký Tài Khoản".

Sau khi hoàn tất, tài khoản mới có vai trò Người dùng, hồ sơ cá nhân được tạo tự động và giao diện tự đăng nhập bằng email và mật khẩu vừa đặt. Nếu bạn vẫn thấy màn hình đăng nhập, hãy đăng nhập thủ công bằng thông tin đó.

Khi quản trị viên tạm đóng đăng ký, bước Email hiển thị "Tạm Đóng Đăng Ký" kèm nội dung "Tính năng đăng ký tài khoản mới hiện đang tạm đóng theo thiết lập của Quản trị viên. Vui lòng quay lại sau!" và bạn chưa thể nhập email.

## Quy định về email, mật khẩu, họ tên và ngày sinh khi đăng ký

Khi đăng ký tài khoản KLTN Social, các trường phải thỏa các điều kiện sau:

- Email: mỗi email chỉ đăng ký được một tài khoản. Email đã có người dùng báo lỗi "Email này đã được sử dụng".
- Mật khẩu: tối thiểu 6 ký tự (máy chủ chỉ bắt buộc mức này). Ô nhập có gợi ý "Tối thiểu 8 ký tự" và thanh "Độ mạnh" (Yếu, Khá an toàn, Rất mạnh) khuyến nghị dùng chữ hoa, số và ký tự đặc biệt, nhưng mật khẩu từ 6 ký tự vẫn được chấp nhận. Mật khẩu ngắn hơn 6 ký tự bị từ chối với thông báo "Dữ liệu đầu vào không hợp lệ".
- Xác nhận mật khẩu phải trùng khớp, nếu không hiện "Mật khẩu xác nhận không khớp!".
- Họ, tên chính và tên đệm: mỗi trường tối đa 50 ký tự. Họ và tên chính là bắt buộc.
- Ngày sinh: bắt buộc, không được ở tương lai và người đăng ký phải đủ 13 tuổi. Dưới 13 tuổi hiện thông báo "Bạn phải từ đủ 13 tuổi trở lên mới được tạo tài khoản!".
- Thiếu trường bắt buộc: "Vui lòng điền đầy đủ các thông tin bắt buộc (*)."

Mã OTP đăng ký có hiệu lực 15 phút. Sau khi xác thực OTP thành công, bạn có 30 phút để hoàn tất bước Thông tin; quá hạn sẽ gặp lỗi "Phiên xác thực email đăng ký không hợp lệ hoặc đã hết hạn" và phải làm lại từ bước Email. Đăng ký bằng số điện thoại hoặc tài khoản mạng xã hội khác chưa được hỗ trợ.

## Cách đăng nhập

Để đăng nhập, vào trang đăng nhập (thẻ "Đăng nhập"), nhập email và mật khẩu rồi bấm "Đăng Nhập". Ô đầu tiên ghi "Email hoặc Tên đăng nhập" nhưng hệ thống hiện chỉ nhận địa chỉ email đã đăng ký; đăng nhập bằng tên đăng nhập hoặc số điện thoại chưa được hỗ trợ.

- Sai email hoặc sai mật khẩu: "Email hoặc mật khẩu không chính xác".
- Để trống: "Vui lòng nhập đầy đủ email và mật khẩu." (hoặc "Vui lòng nhập email và mật khẩu.").
- Ô "Ghi nhớ đăng nhập" có trên form nhưng hiện không làm thay đổi thời gian giữ phiên.
- Liên kết "Quên mật khẩu?" dẫn tới việc lấy lại mật khẩu.

Về phiên đăng nhập: mã truy cập có hiệu lực 15 phút và giao diện tự gia hạn trước khi hết hạn; thông tin gia hạn nằm trong cookie bảo mật có hiệu lực 30 ngày và đổi mới mỗi lần gia hạn. Nếu phiên không còn gia hạn được (đã bị thu hồi, đổi mật khẩu, tài khoản bị cấm), giao diện đưa bạn về trang đăng nhập. Mỗi trình duyệt được ghi nhận là một thiết bị.

## Cách đăng xuất

Để đăng xuất, bấm vào ảnh đại diện ở góc phải thanh tiêu đề để mở menu tài khoản rồi chọn "Đăng xuất". Giao diện xóa dữ liệu đăng nhập trên trình duyệt và đưa bạn về trang đăng nhập; đồng thời máy chủ thu hồi phiên của thiết bị hiện tại (trạng thái "ĐÃ THU HỒI") và xóa cookie gia hạn.

Lưu ý:
- Đăng xuất chỉ áp dụng cho thiết bị bạn đang dùng. Các thiết bị khác vẫn đăng nhập; muốn thoát chúng hãy dùng "Mật khẩu và bảo mật", mục thiết bị đăng nhập.
- Nếu mất mạng đúng lúc đăng xuất, trình duyệt vẫn được dọn sạch nhưng máy chủ có thể chưa kịp thu hồi phiên; bạn nên vào danh sách thiết bị kiểm tra lại.
- Màn hình "Tài khoản của bạn đã bị khóa" (do điểm uy tín thấp) cũng có nút "Đăng xuất".

## Cách lấy lại mật khẩu khi quên (đặt lại mật khẩu bằng OTP)

Nếu quên mật khẩu, bạn đặt lại bằng mã OTP gửi về email đã đăng ký. Các bước:

1. Ở màn hình đăng nhập bấm "Quên mật khẩu?".
2. Nhập "Địa chỉ Email tài khoản" rồi bấm "Gửi Mã Xác Thực OTP". Email không tồn tại hiện lỗi "Email không tồn tại trong hệ thống".
3. Mở thư có tiêu đề "Password Reset Request" (tiêu đề bằng tiếng Anh) để lấy mã 6 chữ số.
4. Nhập "Mã OTP 6 số", "Mật khẩu mới", "Nhập lại mật khẩu" rồi bấm "Đặt Lại Mật Khẩu".
5. Khi thấy "Đặt lại mật khẩu thành công! Hãy đăng nhập bằng mật khẩu mới." hãy đăng nhập lại.

Quy định: mã OTP có hiệu lực 15 phút; mật khẩu mới tối thiểu 6 ký tự ("Mật khẩu mới phải có ít nhất 6 ký tự."); cần nhập đủ 6 chữ số OTP ("Vui lòng nhập mã OTP 6 chữ số."). Mã sai hoặc hết hạn báo "Mã OTP không hợp lệ hoặc đã hết hạn". Sau khi đặt lại thành công, mọi thiết bị của tài khoản bị đăng xuất và các phiên cũ không dùng được nữa.

## Đổi mật khẩu khi đang đăng nhập

Giao diện web hiện chưa có biểu mẫu "Đổi mật khẩu" trong Cài đặt. Trang "Mật khẩu và bảo mật" chỉ có xác thực hai bước và quản lý thiết bị. Muốn đổi mật khẩu, bạn đăng xuất rồi dùng "Quên mật khẩu?" (cần truy cập được email đăng ký) để đặt mật khẩu mới.

Máy chủ có sẵn chức năng đổi mật khẩu (yêu cầu mật khẩu cũ và mật khẩu mới từ 6 ký tự; sai mật khẩu cũ báo "Mật khẩu không chính xác"), nhưng đó là giao diện lập trình chứ chưa có nút trên web. Dù đổi bằng cách nào, sau khi đổi mật khẩu mọi thiết bị của bạn đều bị đăng xuất và phải đăng nhập lại.

## Cách bật xác thực hai bước (2FA, MFA) bằng ứng dụng Authenticator

Xác thực hai bước (MFA, 2FA) thêm một mã 6 số thay đổi mỗi 30 giây khi đăng nhập, tạo bằng ứng dụng Google Authenticator, Microsoft Authenticator hoặc Authy trên điện thoại. Chỉ hỗ trợ loại TOTP này; không có mã qua SMS và giao diện không có lựa chọn nhận mã qua email. Để bật:

1. Bấm ảnh đại diện, chọn "Cài đặt & quyền riêng tư", rồi mục "Mật khẩu và bảo mật".
2. Ở khung "Xác thực 2 yếu tố (2FA TOTP)" (nhãn "CHƯA BẬT") bấm "Thiết lập 2FA ngay".
3. Bước 1: quét mã QR bằng ứng dụng. Bước 2: hoặc nhập thủ công chuỗi khóa hiển thị (có nút sao chép). Hãy lưu khóa này ở nơi an toàn để cài lại ứng dụng khi đổi điện thoại.
4. Bước 3: nhập mã 6 số trong ứng dụng rồi bấm "Kích hoạt 2FA".

Thành công sẽ hiện "Chúc mừng! Xác thực 2 bước (2FA TOTP) đã được kích hoạt bảo vệ tài khoản của bạn." và nhãn "ĐÃ BẢO VỆ". Khóa thiết lập có hiệu lực 10 phút; mã sai báo "Mã OTP không hợp lệ hoặc đã hết hạn". Hệ thống chấp nhận mã lệch giờ khoảng 1 phút, nên nếu mã luôn sai hãy kiểm tra giờ trên điện thoại. Lưu ý: trang này không đọc lại trạng thái 2FA từ máy chủ, nên mở ở phiên trình duyệt mới có thể vẫn hiện "CHƯA BẬT" dù 2FA đang bật; bấm thiết lập lại sẽ thay khóa cũ bằng khóa mới.

## Đăng nhập khi đã bật xác thực hai bước

Khi tài khoản đã bật 2FA, sau khi nhập đúng email và mật khẩu bạn chưa vào ngay mà phải qua bước "Xác thực hai yếu tố": mở ứng dụng Authenticator, nhập mã 6 chữ số vào ô "Mã xác thực TOTP" rồi bấm "Xác Thực Mở Khóa". Muốn bỏ dở bấm "Quay lại đăng nhập".

- Phiên chờ mã chỉ có hiệu lực 5 phút kể từ khi nhập đúng mật khẩu.
- Nhập sai mã sẽ hiện "Mã OTP không chính xác hoặc đã hết hạn. Vui lòng kiểm tra lại Google Authenticator!". Sai 5 lần, phiên bị hủy và hiện "Bạn đã nhập sai quá số lần cho phép, vui lòng yêu cầu mã mới"; khi đó hãy đăng nhập lại từ đầu.
- Nếu đã nhập đúng mã mà vẫn lỗi, hãy đăng nhập lại để có phiên mới (không để quá 5 phút) và kiểm tra giờ điện thoại; nếu vẫn không vào được, liên hệ quản trị viên.

## Cách tắt hoặc cấu hình lại xác thực hai bước

Để tắt 2FA, vào "Cài đặt & quyền riêng tư" > "Mật khẩu và bảo mật", bấm "Tắt 2FA" (hiện khi trạng thái là "ĐÃ BẢO VỆ"), nhập mã 6 số từ ứng dụng Authenticator vào hộp thoại "Xác nhận tắt 2FA" rồi bấm "Xác nhận tắt". Thành công hiện "Đã tắt xác thực 2 bước (2FA) thành công."; mã sai hiện "Không thể tắt 2FA. Mã OTP không hợp lệ." hoặc thông báo từ máy chủ.

Nút "Cấu hình lại" dùng để tạo mã QR mới khi chuyển sang điện thoại khác; sau khi kích hoạt bằng mã mới, ứng dụng cũ không còn dùng được. Nếu giao diện hiện "CHƯA BẬT" dù 2FA đang bật, bạn có thể thiết lập lại (quét mã QR mới, "Kích hoạt 2FA") rồi chọn "Tắt 2FA".

Hệ thống chưa có mã dự phòng (backup code) và chưa có chức năng tự khôi phục khi mất ứng dụng Authenticator và khóa thủ công.

## Quản lý thiết bị đang đăng nhập

Danh sách thiết bị nằm ở "Cài đặt & quyền riêng tư" > "Mật khẩu và bảo mật" > "Quản lý thiết bị đăng nhập", kèm số "thiết bị hoạt động". Mỗi trình duyệt đăng nhập là một thiết bị, hiển thị tên (ví dụ "Chrome on Windows"), địa chỉ IP, thời điểm hoạt động gần nhất và trạng thái "HOẠT ĐỘNG" hoặc "ĐÃ THU HỒI". Thiết bị bạn đang dùng có nhãn "Phiên hiện tại". Cột "Vị trí" luôn hiện mặc định "Việt Nam" vì hệ thống không định vị thật.

- Bấm "Thu hồi" ở một thiết bị khác, xác nhận "Đăng Xuất Thiết Bị Từ Xa" để hủy phiên đó. Thành công hiện "Đã đăng xuất từ xa thiết bị thành công." Lỗi hiện "Không thể thu hồi thiết bị: ...".
- Bấm "Đăng xuất tất cả thiết bị khác" để thu hồi mọi thiết bị trừ thiết bị hiện tại. Nếu không còn thiết bị nào khác: "Không có thiết bị từ xa nào khác đang đăng nhập."
- Nút "Làm mới danh sách thiết bị" tải lại danh sách.

Thiết bị bị thu hồi không làm mới được phiên nữa và trình duyệt đó tự đưa về trang đăng nhập trong khoảng 30 giây. Hiện không có giới hạn số thiết bị đăng nhập cùng lúc.

## Giới hạn nhập sai, gửi lại mã và tần suất yêu cầu

KLTN Social giới hạn các thao tác nhạy cảm để chống dò mật khẩu và gửi mã liên tục:

- Đăng nhập sai mật khẩu 5 lần: tài khoản bị khóa tạm thời tối đa 15 phút với thông báo "Tài khoản tạm thời bị khóa 15 phút do nhập sai mật khẩu quá 5 lần. Vui lòng thử lại sau." Đăng nhập đúng trước đó sẽ xóa bộ đếm sai.
- Gửi mã OTP đăng ký hoặc quên mật khẩu: phải cách nhau ít nhất 60 giây, nếu không hiện "Vui lòng chờ ít nhất 60 giây trước khi yêu cầu gửi lại mã OTP mới."
- Nhập sai OTP (đăng ký hoặc đặt lại mật khẩu) 5 lần: mã bị hủy, hiện "Bạn đã nhập sai quá số lần cho phép, vui lòng yêu cầu mã mới".
- Nhập sai mã 2FA 5 lần: phiên 2FA bị hủy, phải đăng nhập lại.
- Gửi yêu cầu quá nhanh: các thao tác đăng nhập, đăng ký (gửi OTP, xác thực OTP, hoàn tất) và 2FA được tính theo địa chỉ IP; mặc định tối đa 5 yêu cầu liên tiếp rồi nạp lại 1 yêu cầu mỗi 30 giây (quản trị có thể cấu hình khác). Vượt giới hạn hiện "Bạn đang gửi yêu cầu quá nhanh. Vui lòng chờ N giây trước khi thử lại." (N là số giây phải chờ).

## Trạng thái tài khoản: bị cấm, bị khóa, khóa do điểm uy tín

Tài khoản KLTN Social có thể ở trạng thái hoạt động, bị cấm (BANNED) hoặc bị khóa (LOCKED). Khi đăng nhập, bạn có thể gặp:

- Bị cấm: "Tài khoản của bạn đã bị cấm/khóa do vi phạm tiêu chuẩn cộng đồng." Quản trị viên cấm tài khoản sẽ làm mọi thiết bị của bạn bị đăng xuất ngay và bạn nhận thông báo "Tài khoản của bạn đã bị khóa."
- Bị khóa: "Tài khoản của bạn tạm thời bị khóa. Vui lòng liên hệ quản trị viên."
- Khóa do điểm uy tín quá thấp là cơ chế khác: bạn vẫn đăng nhập được nhưng chỉ thấy màn hình "Tài khoản của bạn đã bị khóa" kèm thời điểm mở khóa và thời gian còn lại, không dùng được các chức năng khác cho đến khi hết thời gian khóa. Chi tiết xem tài liệu về điểm uy tín.

Không có chức năng tự xóa tài khoản trên giao diện.

## Câu hỏi thường gặp

### Làm sao để đăng ký tài khoản KLTN Social?

Chọn thẻ "Đăng ký" ở trang đăng nhập, nhập email và bấm "Tiếp Tục & Gửi Mã OTP", nhập mã OTP 6 số nhận qua email, rồi điền họ tên, ngày sinh, giới tính, mật khẩu và bấm "Hoàn Tất Đăng Ký Tài Khoản". Bạn cần từ đủ 13 tuổi và email chưa từng đăng ký.

### Tôi không nhận được email chứa mã OTP thì làm sao?

Kiểm tra thư rác và tìm thư có tiêu đề "Mã xác thực đăng ký tài khoản" (đăng ký) hoặc "Password Reset Request" (quên mật khẩu). Sau khi đếm ngược 60 giây, bấm "Gửi lại mã OTP"; hệ thống không cho gửi lại trong vòng 60 giây. Nhớ kiểm tra email có gõ đúng không, bước OTP có nút "Sửa" để đổi email.

### Mã OTP có hiệu lực bao lâu và được nhập sai mấy lần?

Mã OTP đăng ký và mã OTP đặt lại mật khẩu đều có 6 chữ số, hiệu lực 15 phút và chỉ dùng một lần. Nhập sai 5 lần thì mã bị hủy và bạn phải yêu cầu mã mới. Phiên chờ mã 2FA khi đăng nhập ngắn hơn: 5 phút.

### Mật khẩu cần bao nhiêu ký tự và đăng ký cần điều kiện gì?

Mật khẩu tối thiểu 6 ký tự, nên dùng chữ hoa, số và ký tự đặc biệt để thanh "Độ mạnh" hiện "Rất mạnh". Ngoài ra bạn phải đủ 13 tuổi, họ và tên chính bắt buộc (mỗi trường tối đa 50 ký tự) và mỗi email chỉ lập được một tài khoản.

### Tôi quên mật khẩu thì làm sao?

Bấm "Quên mật khẩu?" ở màn hình đăng nhập, nhập email để nhận mã OTP, sau đó nhập mã cùng mật khẩu mới (tối thiểu 6 ký tự) và bấm "Đặt Lại Mật Khẩu". Sau khi đặt lại, mọi thiết bị cũ bị đăng xuất nên bạn đăng nhập lại bằng mật khẩu mới.

### Làm sao đổi mật khẩu khi đang đăng nhập?

Giao diện web hiện chưa có biểu mẫu đổi mật khẩu. Cách thực tế là đăng xuất rồi dùng "Quên mật khẩu?" để đặt mật khẩu mới qua OTP gửi tới email của bạn.

### Đăng nhập sai mật khẩu nhiều lần thì bị gì?

Sai mật khẩu 5 lần, tài khoản bị khóa đăng nhập tạm thời tối đa 15 phút và báo "Tài khoản tạm thời bị khóa 15 phút do nhập sai mật khẩu quá 5 lần. Vui lòng thử lại sau." Hết thời gian chờ bạn thử lại; nếu quên mật khẩu hãy dùng "Quên mật khẩu?".

### Tôi có thể đăng nhập bằng tên đăng nhập hoặc số điện thoại không?

Không. Dù ô nhập ghi "Email hoặc Tên đăng nhập", hệ thống hiện chỉ đăng nhập bằng địa chỉ email đã dùng để đăng ký cùng mật khẩu.

### Làm sao bật xác thực hai bước (2FA)?

Vào "Cài đặt & quyền riêng tư" > "Mật khẩu và bảo mật", bấm "Thiết lập 2FA ngay", quét mã QR bằng Google Authenticator, Microsoft Authenticator hoặc Authy, nhập mã 6 số và bấm "Kích hoạt 2FA". Chỉ hỗ trợ ứng dụng Authenticator (TOTP), không có SMS.

### Mất điện thoại hoặc ứng dụng Authenticator thì làm sao?

Hệ thống chưa có mã dự phòng và chưa có luồng tự khôi phục 2FA. Nếu còn lưu "khóa thủ công" lúc thiết lập, bạn nhập lại khóa đó vào ứng dụng trên điện thoại mới để lấy mã. Nếu không, hãy liên hệ quản trị viên để được hỗ trợ.

### Làm sao đăng xuất khỏi một thiết bị khác?

Vào "Mật khẩu và bảo mật", tìm thiết bị trong "Quản lý thiết bị đăng nhập" và bấm "Thu hồi", hoặc bấm "Đăng xuất tất cả thiết bị khác". Thiết bị bị thu hồi sẽ hiện "ĐÃ THU HỒI" và tự về trang đăng nhập trong khoảng 30 giây.

### Tài khoản của tôi bị cấm hoặc bị khóa thì sao?

Bị cấm hiện "Tài khoản của bạn đã bị cấm/khóa do vi phạm tiêu chuẩn cộng đồng."; bị khóa hiện "Tài khoản của bạn tạm thời bị khóa. Vui lòng liên hệ quản trị viên." Nếu chỉ thấy màn hình "Tài khoản của bạn đã bị khóa" kèm thời điểm mở khóa thì đó là khóa do điểm uy tín thấp và tài khoản tự mở lại khi hết thời gian.
