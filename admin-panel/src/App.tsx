import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import MessagesPage from './pages/MessagesPage';
import NewsPage from './pages/NewsPage';
import UsersPage from './pages/UsersPage';
import TasksPage from './pages/TasksPage';
import TicketsPage from './pages/TicketsPage';
import ConfigPage from './pages/ConfigPage';
import AuditPage from './pages/AuditPage';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600" />
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/*"
        element={
          <ProtectedRoute>
            <Layout>
              <Routes>
                <Route path="/" element={<DashboardPage />} />
                <Route path="/messages" element={<MessagesPage />} />
                <Route path="/news" element={<NewsPage />} />
                <Route path="/users" element={<UsersPage />} />
                <Route path="/tasks" element={<TasksPage />} />
                <Route path="/tickets" element={<TicketsPage />} />
                <Route path="/audit" element={<AuditPage />} />
                <Route path="/config" element={<ConfigPage />} />
              </Routes>
            </Layout>
          </ProtectedRoute>
        }
      />
    </Routes>
  );
}
