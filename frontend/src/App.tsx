import React, { useEffect } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuthStore } from './store/authStore'
import { authApi } from './api/authApi'
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
  const user = useAuthStore((s) => s.user)
  const setUser = useAuthStore((s) => s.setUser)
  const logout = useAuthStore((s) => s.logout)

  useEffect(() => {
    if (isAuthenticated && !user) {
      authApi.me().then(setUser).catch(() => logout())
    }
  }, [isAuthenticated, user, setUser, logout])

  return (
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
  )
}
