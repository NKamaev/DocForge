import React, { useEffect } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { ToastContainer, TooltipProvider } from '@/components/ui'
import { useAuthStore } from './store/authStore'
import { authApi } from './api/authApi'
import { getErrorStatus } from './api/errorMessages'
import LoginPage from './pages/LoginPage'
import HomePage from './pages/HomePage'
import TemplatesPage from './pages/TemplatesPage'
import TemplateDesignerPage from './pages/TemplateDesignerPage'
import AppLayout from './components/AppLayout'
import QuestionnairePage from './components/questionnaire/QuestionnairePage'
import DocumentsPage from './pages/DocumentsPage'
import DirectoriesPage from './pages/DirectoriesPage'
import DirectoryDetailPage from './pages/DirectoryDetailPage'

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const auth = useAuthStore((s) => s.isAuthenticated)
  return auth ? <>{children}</> : <Navigate to="/login" replace />
}

export default function App() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const setUser = useAuthStore((s) => s.setUser)
  const markUserResolved = useAuthStore((s) => s.markUserResolved)
  const logout = useAuthStore((s) => s.logout)

  // Профиль всегда синхронизируется с сервером: authStore отдаёт закэшированные
  // данные сразу (чтобы права на ресурс были известны без мигания кнопок), а
  // этот запрос их обновляет — в том числе флаг admin.
  useEffect(() => {
    if (!isAuthenticated) return
    let cancelled = false
    authApi.me()
      .then((u) => { if (!cancelled) setUser(u) })
      .catch((err: unknown) => {
        if (cancelled) return
        // Сессия недействительна — выходим. Временная сетевая ошибка не должна
        // выкидывать пользователя: закэшированный профиль остаётся актуальным.
        const status = getErrorStatus(err)
        if (status === 401 || status === 403) {
          logout()
          return
        }
        // Профиль выяснить не удалось: помечаем как «выяснен», чтобы
        // интерфейс не ждал его бесконечно. Права остаются закрытыми —
        // `user == null` → действия владельца не показываются.
        markUserResolved()
      })
    return () => { cancelled = true }
  }, [isAuthenticated, setUser, markUserResolved, logout])

  return (
    <TooltipProvider>
      <div
        data-density="regular"
        data-theme="light"
        data-fonts="grotesk"
        style={{ "--brand-h": "232" } as React.CSSProperties}
      >
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/"
            element={
              <PrivateRoute>
                <AppLayout />
              </PrivateRoute>
            }
          >
            <Route index element={<HomePage />} />
            <Route path="templates" element={<TemplatesPage />} />
            <Route path="templates/:id/edit" element={<TemplateDesignerPage />} />
            <Route path="documents" element={<DocumentsPage />} />
            <Route path="documents/:id/fill" element={<QuestionnairePage />} />
            <Route path="directories" element={<DirectoriesPage />} />
            <Route path="directories/:id" element={<DirectoryDetailPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </div>
      <ToastContainer />
    </TooltipProvider>
  )
}
