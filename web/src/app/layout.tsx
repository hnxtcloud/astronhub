import { Suspense, useEffect, useRef, useState } from 'react'
import { Outlet, Link, useRouterState } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Menu, X } from 'lucide-react'
import { useAuth } from '@/features/auth/use-auth'
import { LanguageSwitcher } from '@/shared/components/language-switcher'
import { ThemeToggle } from '@/shared/components/theme-toggle'
import { UserMenu } from '@/shared/components/user-menu'
import { NotificationBell } from '@/features/notification/notification-bell'
import { dismissOpenOverlays } from '@/shared/lib/dismiss-open-overlays'
import { syncDocumentLanguage } from '@/shared/lib/document-language'
import { DashboardSidebar, SIDEBAR_GROUPS } from '@/pages/dashboard'
import { canViewGovernanceCenter } from '@/shared/lib/governance-access'
import { getAppHeaderClassName } from './layout-header-style'
import { AUTH_ENTRY_PATHS, SPLIT_AUTH_PATHS, getAppMainContentLayout, resolveAppMainContentPathname } from './layout-main-content'

/**
 * Application shell shared by all routed pages.
 *
 * It owns the global header, footer, language switcher, auth-aware navigation, and suspense
 * fallback used while lazy route modules are loading.
 */
export function Layout() {
  const { t, i18n } = useTranslation()
  const { pathname, resolvedPathname } = useRouterState({
    select: (s) => ({
      pathname: s.location.pathname,
      resolvedPathname: s.resolvedLocation?.pathname,
    }),
  })
  const { user, isLoading } = useAuth()
  const [isHeaderElevated, setIsHeaderElevated] = useState(false)
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const previousPathnameRef = useRef(pathname)
  const contentLayoutPathname = resolveAppMainContentPathname(pathname, resolvedPathname)
  const mainContentLayout = getAppMainContentLayout(contentLayoutPathname)
  const isAuthEntryRoute = AUTH_ENTRY_PATHS.includes(contentLayoutPathname as typeof AUTH_ENTRY_PATHS[number])
  const isSplitAuthRoute = SPLIT_AUTH_PATHS.includes(contentLayoutPathname as typeof SPLIT_AUTH_PATHS[number])
  const isDashboardSubRoute = pathname !== '/dashboard' && pathname.startsWith('/dashboard')
  const showSidebar = (isDashboardSubRoute && pathname !== '/dashboard/publish') || pathname.startsWith('/settings/')
  const governanceVisible = canViewGovernanceCenter(user?.platformRoles)
  const filteredDashboardGroups = SIDEBAR_GROUPS
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => (
        (!item.admin || governanceVisible)
        && (!item.passwordCapability || user?.canChangePassword === true)
      )),
    }))
    .filter((group) => group.items.length > 0)

  useEffect(() => {
    syncDocumentLanguage(i18n.resolvedLanguage ?? i18n.language)
  }, [i18n.language, i18n.resolvedLanguage])

  useEffect(() => {
    const updateHeaderElevation = () => {
      setIsHeaderElevated(window.scrollY > 0)
    }

    updateHeaderElevation()
    window.addEventListener('scroll', updateHeaderElevation, { passive: true })

    return () => {
      window.removeEventListener('scroll', updateHeaderElevation)
    }
  }, [])

  // Pathname-only: search debounce on /search must not dismiss overlays mid-typing.
  useEffect(() => {
    if (previousPathnameRef.current === pathname) {
      return
    }
    previousPathnameRef.current = pathname
    dismissOpenOverlays()
  }, [pathname])

  const navItems: Array<{
    label: string
    to: string
    exact?: boolean
    auth?: boolean
  }> = [
    { label: t('nav.landing'), to: '/', exact: true },
    { label: t('nav.catalog'), to: '/catalog' },
    { label: t('nav.plugins'), to: '/plugins' },
    { label: t('nav.mcp'), to: '/mcp-servers' },
    { label: t('nav.software'), to: '/software' },
    { label: t('nav.suites'), to: '/suites' },
    { label: t('nav.publish'), to: '/dashboard/publish', auth: true },
    { label: t('nav.dashboard'), to: '/dashboard', auth: true },
  ]

  const isActive = (to: string, exact?: boolean) => {
    if (exact) return pathname === to
    // 「控制台」按钮：仅在 /dashboard 主页或 /settings/* 时高亮
    if (to === '/dashboard') {
      return pathname === '/dashboard' || pathname.startsWith('/settings/')
    }
    // Keep matching strict so parent dashboard paths do not highlight unrelated child links.
    return pathname === to
  }

  return (
    <div className="min-h-screen flex flex-col relative" style={{ background: 'var(--bg-page, hsl(var(--background)))' }}>
      {/* Header */}
      <header
        className={isSplitAuthRoute ? 'absolute right-5 top-4 z-50 flex items-center sm:right-10' : getAppHeaderClassName(isHeaderElevated)}
        style={isSplitAuthRoute ? undefined : { borderColor: 'hsl(var(--border))' }}
      >
        {!isSplitAuthRoute ? (
          <div className="mx-auto flex h-16 w-full max-w-[1440px] items-center justify-between px-6 md:px-10">
            <Link to="/" className="flex items-baseline gap-2 text-foreground">
              <span className="text-lg font-semibold tracking-tight">星枢</span>
              <span className="hidden text-xs font-medium text-muted-foreground sm:inline">AstronHub</span>
            </Link>

            <nav className="hidden md:flex items-center gap-1 text-sm" style={{ color: 'hsl(var(--text-secondary))' }}>
              {navItems.map((item) => {
                if (item.auth && !user) return null
                const active = isActive(item.to, item.exact)

                return (
                  <Link
                    key={item.to}
                    to={item.to}
                    className={
                      active
                        ? 'rounded-md bg-secondary px-3 py-2 font-medium text-foreground'
                        : 'rounded-md px-3 py-2 transition-colors duration-150 hover:bg-secondary/60 hover:text-foreground'
                    }
                  >
                    {item.label}
                  </Link>
                )
              })}
            </nav>

            <div className="flex items-center gap-4 text-sm" style={{ color: 'hsl(var(--text-secondary))' }}>
              {/* Hamburger — visible below md */}
              <button
                type="button"
                className="md:hidden inline-flex items-center justify-center rounded-md p-2 hover:bg-secondary/60 hover:text-foreground transition-colors"
                onClick={() => setMobileMenuOpen((prev) => !prev)}
                aria-expanded={mobileMenuOpen}
                aria-label={t(mobileMenuOpen ? 'layout.closeNavigation' : 'layout.openNavigation')}
              >
                {mobileMenuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
              </button>
              <ThemeToggle />
              <LanguageSwitcher />
              {user && <NotificationBell />}
              {isLoading ? null : user ? (
                <UserMenu user={user} />
              ) : (
                <Link
                  to="/login"
                  className="rounded-md px-2 py-1.5 transition-colors hover:bg-secondary/60 hover:text-foreground"
                >
                  {t('nav.login')}
                </Link>
              )}
            </div>
          </div>
        ) : (
          <div className="flex items-center gap-4 text-sm" style={{ color: 'hsl(var(--text-secondary))' }}>
            <ThemeToggle />
            <LanguageSwitcher />
            {isLoading ? null : user ? (
              <UserMenu user={user} />
            ) : null}
          </div>
        )}
      </header>

      {/* Mobile nav dropdown */}
      {mobileMenuOpen && !isSplitAuthRoute ? (
        <div className="md:hidden sticky top-16 z-40 border-b bg-background/95 backdrop-blur" style={{ borderColor: 'hsl(var(--border))' }}>
          <nav className="flex flex-col px-4 py-3 gap-1">
            {navItems.map((item) => {
              if (item.auth && !user) return null
              const active = isActive(item.to, item.exact)
              return (
                <Link
                  key={item.to}
                  to={item.to}
                  className={`rounded-md px-3 py-2.5 text-sm font-medium transition-colors ${
                    active ? 'bg-secondary text-foreground' : 'hover:bg-secondary/60 hover:text-foreground'
                  }`}
                  style={active ? undefined : { color: 'hsl(var(--text-secondary))' }}
                  onClick={() => setMobileMenuOpen(false)}
                >
                  {item.label}
                </Link>
              )
            })}
          </nav>
        </div>
      ) : null}

      {/* Main content */}
      <main className={mainContentLayout.mainClassName}>
        <Suspense
          fallback={
            <div className="space-y-4 animate-fade-up">
              <div className="h-10 w-48 animate-shimmer rounded-lg" />
              <div className="h-5 w-72 animate-shimmer rounded-md" />
              <div className="h-64 animate-shimmer rounded-xl" />
            </div>
          }
        >
          <div className={mainContentLayout.contentClassName}>
            {showSidebar ? (
              <div className="flex flex-col lg:flex-row gap-6">
                <DashboardSidebar groups={filteredDashboardGroups} user={user} t={t} pathname={pathname} />
                <div className="flex-1 min-w-0">
                  <Outlet />
                </div>
              </div>
            ) : (
              <Outlet />
            )}
          </div>
        </Suspense>
      </main>

      {/* Footer */}
      {!isAuthEntryRoute ? (
        <footer className="relative z-10 mt-auto border-t bg-white" style={{ borderColor: 'hsl(var(--border))' }}>
          <div className="max-w-6xl mx-auto px-6 py-10 md:px-10">
            <div className="flex flex-col md:flex-row md:items-start md:justify-between gap-10 md:gap-12">
              <div className="flex-shrink-0">
                <div className="flex items-center gap-2 mb-3">
                  <div className="flex h-8 w-8 items-center justify-center rounded-md bg-primary text-sm font-semibold text-primary-foreground">
                    星
                  </div>
                  <span className="text-base font-semibold text-foreground">星枢</span>
                </div>
                <p className="text-sm max-w-xs" style={{ color: 'hsl(var(--text-secondary))' }}>
                  {t('layout.footerDescription')}
                </p>
              </div>
              <div className="flex flex-wrap gap-12 md:gap-16">
                <div>
                  <h4 className="text-sm font-semibold mb-3" style={{ color: 'hsl(var(--foreground))' }}>
                    {t('nav.home')}
                  </h4>
                  <ul className="space-y-2 text-sm">
                    <li>
                      <Link to="/" className="hover:opacity-80 transition-opacity" style={{ color: 'hsl(var(--text-secondary))' }}>
                        {t('nav.home')}
                      </Link>
                    </li>
                    <li>
                      <Link
                        to="/search"
                        search={{ q: '', sort: 'relevance', page: 0, starredOnly: false }}
                        className="hover:opacity-80 transition-opacity"
                        style={{ color: 'hsl(var(--text-secondary))' }}
                      >
                        {t('nav.search')}
                      </Link>
                    </li>
                    <li>
                      <Link to="/dashboard" className="hover:opacity-80 transition-opacity" style={{ color: 'hsl(var(--text-secondary))' }}>
                        {t('nav.dashboard')}
                      </Link>
                    </li>
                  </ul>
                </div>
                <div>
                  <h4 className="text-sm font-semibold mb-3" style={{ color: 'hsl(var(--foreground))' }}>
                    {t('footer.resources')}
                  </h4>
                  <ul className="space-y-2 text-sm">
                    <li>
                      <a href="#" className="hover:opacity-80 transition-opacity" style={{ color: 'hsl(var(--text-secondary))' }}>
                        {t('footer.docs')}
                      </a>
                    </li>
                    <li>
                      <a href="#" className="hover:opacity-80 transition-opacity" style={{ color: 'hsl(var(--text-secondary))' }}>
                        {t('footer.api')}
                      </a>
                    </li>
                    <li>
                      <a href="#" className="hover:opacity-80 transition-opacity" style={{ color: 'hsl(var(--text-secondary))' }}>
                        {t('footer.community')}
                      </a>
                    </li>
                  </ul>
                </div>
              </div>
            </div>
            <div
              className="mt-10 pt-6 border-t flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 text-xs"
              style={{ borderColor: 'hsl(var(--border))', color: 'hsl(var(--muted-foreground))' }}
            >
              <span>{t('footer.copyright')}</span>
              <div className="flex items-center gap-2">
                <Link to="/privacy" className="hover:opacity-80 transition-opacity">
                  {t('footer.privacy')}
                </Link>
                <span>|</span>
                <Link to="/terms" className="hover:opacity-80 transition-opacity">
                  {t('footer.terms')}
                </Link>
              </div>
            </div>
          </div>
        </footer>
      ) : null}
    </div>
  )
}
