# OpSecurity
* Một plugin minecraft server có chức năng bảo vệ các staff, admin khỏi những kẻ hack tài khoản, check pass v.v
* Cơ chế:
  + Khi staff đăng kí /opsec register thì sẽ được bảo vệ trong cụm máy chủ cụ thể
  + Khi đăng nhập lại cần phải ghi mật khẩu trong chat hoặc sử dụng /opsec login <mật khẩu> để có thể chơi
  + Mọi hành động như tương tác, các lệnh khác trừ /opsec đều sẽ bị kick nhằm bảo mật tài khoản tuyệt đối
 Quyền và lệnh:
  opsec:
    description: Lệnh bảo mật và quản lý staff
    usage: /<command> [register|login|forgot|check|contactadmin|reset|update|reload] [args]
  addstaff:
    description: Thêm staff vào rank trong staff.yml
    usage: /<command> <rank> <player>
    permission: opsecurity.addstaff
  removestaff:
    description: Xóa staff khỏi rank trong staff.yml
    usage: /<command> <rank> <player>
    permission: opsecurity.removestaff
permissions:
  security.staff:
    description: Quyền dành cho staff
    default: op
  opsecurity.addstaff:
    description: Quyền thêm staff
    default: op
  opsecurity.removestaff:
    description: Quyền xóa staff
    default: op
  opsecurity.check:
    description: Quyền kiểm tra rank
    default: op
  opsecurity.reset:
    description: Quyền reset mật khẩu thủ công
    default: op
  opsecurity.update:
    description: Quyền kiểm tra và cập nhật plugin
    default: op
  opsecurity.reload:
    description: Quyền tải lại dữ liệu plugin
    default: op
### Plugin được lấy ý tưởng từ BungeeGroup của AEMINE.VN
