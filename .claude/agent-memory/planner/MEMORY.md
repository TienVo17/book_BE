# Planner Agent Memory

## Project: book_BE-main + book_fe-master (Bookstore)

### Stack
- Backend: Spring Boot 3.3.4, Java 17, MySQL 8, JWT auth, VNPay payments
- Frontend: React 18 + TypeScript, react-router-dom v6, react-toastify, Bootstrap 5
- No axios, no state management lib. Uses native fetch() + useState/useEffect

### Frontend Conventions
- API files: PascalCase in `src/api/`, hardcode `http://localhost:8080` per file
- Models: PascalCase classes with constructors in `src/models/`
- Auth: JWT in `localStorage.getItem('jwt')`, decoded via `atob(jwt.split('.')[1])`
- `my_request()` in Request.ts = public GET only (no auth header)
- Auth calls use inline fetch with `Authorization: Bearer {token}` header
- CSS: var(--color-*) CSS variables, auth-container/auth-card/auth-input for auth pages
- Toast: `import { toast } from 'react-toastify'`

### Key File Locations
- Frontend root: `e:\BT\Stack1\book_fe-master\`
- Backend root: `e:\BT\Stack1\book_BE-main\`
- Plans: `E:\BT\Stack1\book_BE-main\plans\`

### Gotchas
- ThanhToan.tsx auto-creates order on mount via useEffect -- any checkout changes must refactor this
- ProtectedRoute is INVERTED: redirects to "/" if JWT EXISTS (guards login/register from logged-in users)
- Some API endpoints use old Spring Data REST paths (e.g., `/sach/{id}`, `/sach/{id}/listHinhAnh`)
- HinhAnhApi parses `response._embedded.hinhAnhs` (Spring Data REST format)
- UploadFile.tsx converts images to base64/WebP client-side, not multipart upload
