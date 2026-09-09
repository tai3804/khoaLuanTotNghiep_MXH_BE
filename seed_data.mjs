// Script to seed 10 users and 5 posts per user
const GATEWAY_URL = 'http://localhost:8080/api/v1';

const sampleUsers = [
  {
    firstName: 'An',
    lastName: 'Nguyễn Văn',
    email: 'an.nguyen@example.com',
    password: 'Password123@',
    bio: 'Đam mê công nghệ, lập trình React & Java Spring Boot.',
    avatarUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150',
    posts: [
      'Hôm nay bắt đầu nghiên cứu kiến trúc Microservices với Spring Cloud và Eureka. Cực kỳ thú vị!',
      'Vừa hoàn thành module xác thực OAuth2 và JWT trên Spring Gateway. Mọi người có kinh nghiệm optimize gateway không?',
      'Cà phê sáng cùng ly espresso đậm đà trước khi bước vào buổi review đồ án tốt nghiệp ☕💻',
      'Chia sẻ một mẹo nhỏ khi cấu hình Docker Compose với PostgreSQL: hãy luôn tạo file init SQL để tự động khởi tạo multiple databases!',
      'Cuối tuần rồi, chúc anh em lập trình viên có những ngày nghỉ ngơi thư giãn và sạc lại năng lượng 🌟'
    ]
  },
  {
    firstName: 'Bình',
    lastName: 'Trần Thị',
    email: 'binh.tran@example.com',
    password: 'Password123@',
    bio: 'UI/UX Designer yêu thích sự tối giản và trải nghiệm người dùng.',
    avatarUrl: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150',
    posts: [
      'Thiết kế giao diện Dark mode không chỉ là đảo ngược màu trắng thành đen. Độ tương phản và độ sâu mới là chìa khóa!',
      'Figma vừa cập nhật các tính năng component properties mới, tiết kiệm đến 50% thời gian xây dựng design system.',
      'Một góc làm việc gọn gàng sẽ mang lại sự tập trung và sáng tạo cao hơn rất nhiều.',
      'Buổi thuyết trình về xu hướng thiết kế Neumorphism và Glassmorphism trong các ứng dụng mạng xã hội hiện đại.',
      'Sách hay đầu tuần: "Don\'t Make Me Think" - cuốn cẩm nang gối đầu giường của mọi designer.'
    ]
  },
  {
    firstName: 'Cường',
    lastName: 'Lê Hoàng',
    email: 'cuong.le@example.com',
    password: 'Password123@',
    bio: 'DevOps Engineer | Cloud Computing & Kubernetes Enthusiast.',
    avatarUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150',
    posts: [
      'Triển khai CI/CD pipeline tự động build Docker image và deploy lên cụm Kubernetes chỉ với 1 cú click.',
      'Tại sao nên dùng Kafka thay vì RabbitMQ khi xử lý luồng dữ liệu thời gian thực hàng triệu tin nhắn mỗi giây?',
      'Monitor hệ thống toàn diện với bộ ba Prometheus, Grafana và Loki. Cảnh báo lỗi gửi ngay về Telegram bot!',
      'Chi phí AWS tháng này đã được tối ưu giảm 35% nhờ áp dụng Spot Instances và Auto Scaling hợp lý.',
      'Tham gia hội thảo Cloud Native Summit 2026 hôm nay, học hỏi được rất nhiều case study thực tế từ các doanh nghiệp lớn.'
    ]
  },
  {
    firstName: 'Đức',
    lastName: 'Phạm Minh',
    email: 'duc.pham@example.com',
    password: 'Password123@',
    bio: 'Mobile App Developer (Flutter & React Native). Yêu thích du lịch phượt.',
    avatarUrl: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150',
    posts: [
      'Trải nghiệm Flutter 3.x trên cả Android và iOS: mượt mà, hiệu năng không thua kém native là bao.',
      'Hôm nay chinh phục cung đường đèo Mã Pí Lèng - Hà Giang. Thiên nhiên Việt Nam hùng vĩ đến ngỡ ngàng 🏔️🏍️',
      'Tip quản lý state trong React Native: Zustand thực sự nhẹ nhàng và dễ tiếp cận hơn Redux Toolkit rất nhiều.',
      'Sau 3 tháng miệt mài, ứng dụng mạng xã hội sinh viên cuối cùng cũng lên kệ Google Play Store!',
      'Ghé quán quen thưởng thức bát phở bò nóng hổi giữa tiết trời se lạnh đầu mùa.'
    ]
  },
  {
    firstName: 'Hà',
    lastName: 'Hoàng Thu',
    email: 'ha.hoang@example.com',
    password: 'Password123@',
    bio: 'Data Analyst & AI Enthusiast. Thích đọc sách và làm bánh.',
    avatarUrl: 'https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=150',
    posts: [
      'Trực quan hóa dữ liệu người dùng với PowerBI và Python Seaborn mang lại những góc nhìn rất bất ngờ.',
      'Ứng dụng mô hình xử lý ngôn ngữ tự nhiên (NLP) để phân tích cảm xúc các bình luận trên mạng xã hội.',
      'Cuối tuần trổ tài làm bánh tiramisu cho cả gia đình. Vị thơm của cacao kết hợp kem cheese béo ngậy 🍰',
      'Dữ liệu không biết nói dối, nhưng cách chúng ta đặt câu hỏi với dữ liệu sẽ quyết định câu trả lời nhận được.',
      'Khóa học Deep Learning Specialization vừa hoàn thành! Mục tiêu tiếp theo là nghiên cứu các mô hình LLM mã nguồn mở.'
    ]
  },
  {
    firstName: 'Huy',
    lastName: 'Đặng Quốc',
    email: 'huy.dang@example.com',
    password: 'Password123@',
    bio: 'Cyber Security Analyst | Ethical Hacker.',
    avatarUrl: 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=150',
    posts: [
      'Luôn bật xác thực 2 bước (2FA) bằng Authenticator App thay vì SMS OTP để tránh rủi ro SIM swapping.',
      'Phát hiện và xử lý lỗ hổng SQL Injection tiềm ẩn trong truy vấn động. Luôn dùng Parameterized Queries!',
      'Cảnh giác với các chiến dịch Phishing mạo danh ngân hàng và mạng xã hội ngày càng tinh vi hiện nay.',
      'Tham gia giải đấu CTF (Capture The Flag) cuối tuần cùng team, leo top 10 bảng tổng sắp toàn quốc!',
      'An toàn thông tin không phải là một đích đến, mà là một quy trình liên tục không ngừng nghỉ.'
    ]
  },
  {
    firstName: 'Mai',
    lastName: 'Vũ Thị',
    email: 'mai.vu@example.com',
    password: 'Password123@',
    bio: 'Product Manager. Đam mê xây dựng các sản phẩm công nghệ có ích cho cộng đồng.',
    avatarUrl: 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=150',
    posts: [
      'Một sản phẩm tốt bắt đầu từ việc lắng nghe những nỗi đau (pain points) thực sự của người dùng.',
      'Buổi họp Sprint Planning đầu tuần tràn đầy năng lượng cùng toàn thể anh em kỹ sư phát triển.',
      'A/B testing trên tính năng đăng bài mới cho thấy tỷ lệ tương tác tăng trưởng vượt bậc 28%!',
      'Cân bằng giữa deadline kinh doanh và nợ kỹ thuật (technical debt) luôn là bài toán đau đầu nhất của PM.',
      'Chúc mừng cả team đã đạt cột mốc 10.000 active users trong tháng đầu tiên ra mắt!'
    ]
  },
  {
    firstName: 'Nam',
    lastName: 'Bùi Tuấn',
    email: 'nam.bui@example.com',
    password: 'Password123@',
    bio: 'Fullstack Developer | JavaScript & Spring Boot. Yêu thể thao, chạy bộ.',
    avatarUrl: 'https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=150',
    posts: [
      'Chạy bộ 10km sáng sớm để nạp năng lượng cho ngày làm việc căng thẳng 🏃‍♂️',
      'Vite và React 19 kết hợp cùng TailwindCSS mang lại trải nghiệm phát triển frontend cực nhanh chóng.',
      'Tối ưu hóa performance database indexing giúp giảm response time của API từ 450ms xuống còn 18ms!',
      'Học hỏi kiến trúc Event-Driven Architecture: phân tách hệ thống lỏng lẻo giúp scale cực kỳ dễ dàng.',
      'Sách hay gợi ý cho anh em lập trình: "Clean Code" và "Designing Data-Intensive Applications".'
    ]
  },
  {
    firstName: 'Thảo',
    lastName: 'Đỗ Phương',
    email: 'thao.do@example.com',
    password: 'Password123@',
    bio: 'Content Creator & Marketer. Thích khám phá ẩm thực đường phố.',
    avatarUrl: 'https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=150',
    posts: [
      'Content is King, nhưng Context is Queen. Nội dung hay phải được phân phối đúng người đúng thời điểm!',
      'Review quán ốc đêm cực ngon gần trường Đại học Công nghiệp TP.HCM (IUH), giá sinh viên mà chất lượng đỉnh.',
      'Xu hướng video ngắn (Shorts / Reels / TikTok) đang định hình lại cách người dùng tiếp nhận thông tin.',
      'Gặp gỡ và trò chuyện với các bạn trẻ tài năng tại Ngày hội việc làm công nghệ 2026.',
      'Một nụ cười bằng mười thang thuốc bổ, chúc mọi người một ngày làm việc ngập tràn niềm vui!'
    ]
  },
  {
    firstName: 'Vinh',
    lastName: 'Ngô Quang',
    email: 'vinh.ngo@example.com',
    password: 'Password123@',
    bio: 'Học viên IUH FIT. Làm đồ án tốt nghiệp mạng xã hội.',
    avatarUrl: 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=150',
    posts: [
      'Đang hoàn thiện những tính năng cuối cùng cho đồ án Khóa Luận Tốt Nghiệp Mạng Xã Hội Đa Dịch Vụ!',
      'Cảm ơn thầy cô khoa CNTT - Đại học Công nghiệp TP.HCM đã tận tình hướng dẫn trong suốt thời gian qua.',
      'Hệ thống kết hợp Microservices Spring Boot và ReactJS hoạt động rất trơn tru và ổn định.',
      'Thử nghiệm tính năng bình luận và cảm xúc thời gian thực hoạt động rất mượt.',
      'Đếm ngược ngày bảo vệ khóa luận. Tự tin bước vào chặng đường mới phía trước! 🎓🎉'
    ]
  }
];

async function seed() {
  console.log('--- BẮT ĐẦU TẠO 10 USER VÀ 50 BÀI VIẾT ---');
  let totalPostsCreated = 0;

  for (let i = 0; i < sampleUsers.length; i++) {
    const u = sampleUsers[i];
    console.log(`\n[${i + 1}/10] Xử lý User: ${u.lastName} ${u.firstName} (${u.email})...`);

    // 1. Register user
    try {
      const regRes = await fetch(`${GATEWAY_URL}/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: u.email,
          password: u.password,
          firstName: u.firstName,
          lastName: u.lastName
        })
      });
      const regData = await regRes.json();
      if (!regRes.ok && !regData?.message?.includes('already')) {
        console.log(`   - Đăng ký: ${regData?.message || regRes.statusText}`);
      } else {
        console.log(`   - Đăng ký thành công / tài khoản đã sẵn sàng.`);
      }
    } catch (e) {
      console.log(`   - Lỗi đăng ký: ${e.message}`);
    }

    // 2. Login user to get JWT
    let token = null;
    try {
      const loginRes = await fetch(`${GATEWAY_URL}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: u.email,
          password: u.password,
          deviceFingerprint: `seed-device-${i + 1}`,
          deviceName: 'Web Seed Client'
        })
      });
      const loginData = await loginRes.json();
      token = loginData?.data?.accessToken || loginData?.data?.token;
      if (!token) {
        console.log(`   - Không lấy được token:`, loginData);
        continue;
      }
      console.log(`   - Đăng nhập thành công, lấy được Access Token.`);
    } catch (e) {
      console.log(`   - Lỗi đăng nhập: ${e.message}`);
      continue;
    }

    // 3. Update profile (avatar, bio)
    try {
      await fetch(`${GATEWAY_URL}/users/profile/me`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({
          firstName: u.firstName,
          lastName: u.lastName,
          bio: u.bio,
          avatarUrl: u.avatarUrl
        })
      });
      console.log(`   - Cập nhật hồ sơ & avatar thành công.`);
    } catch (e) {
      console.log(`   - Bỏ qua cập nhật profile: ${e.message}`);
    }

    // 4. Create 5 posts
    for (let pIdx = 0; pIdx < u.posts.length; pIdx++) {
      const postContent = u.posts[pIdx];
      try {
        const formData = new FormData();
        formData.append('content', postContent);
        formData.append('privacy', 'PUBLIC');

        const postRes = await fetch(`${GATEWAY_URL}/posts`, {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${token}`
          },
          body: formData
        });
        const postData = await postRes.json();
        if (postRes.ok) {
          totalPostsCreated++;
          console.log(`     + Đã tạo bài viết ${pIdx + 1}/5`);
        } else {
          console.log(`     x Lỗi tạo bài viết ${pIdx + 1}:`, postData?.message || postRes.statusText);
        }
      } catch (e) {
        console.log(`     x Lỗi tạo bài viết ${pIdx + 1}: ${e.message}`);
      }
    }
  }

  console.log(`\n==============================================`);
  console.log(`HOÀN TẤT: Đã tạo thành công dữ liệu cho 10 user với tổng cộng ${totalPostsCreated} bài viết.`);
  console.log(`Tất cả mật khẩu của 10 user là: Password123@`);
  console.log(`==============================================`);
}

seed();
