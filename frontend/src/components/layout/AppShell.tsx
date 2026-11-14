import { Ellipsis, GraduationCap, LogOut, Settings, type LucideIcon } from "lucide-react";
import { useState } from "react";
import { Link, NavLink, Outlet } from "react-router";
import { useUnreadCount } from "@/api/queries/notification";
import { useActor } from "@/auth/useActor";
import { useLogout } from "@/auth/useLogout";
import { cn } from "@/lib/utils";
import { HeaderActions } from "./HeaderActions";
import { navForRoles, type NavItem } from "./nav";

const SIDE_LINK =
  "flex h-11 items-center gap-3 rounded-xl px-3 text-sm font-medium text-[#3a3f4a] hover:bg-canvas md:max-lg:justify-center md:max-lg:px-0";

function CountBadge({ count }: { count: number }) {
  if (count <= 0) return null;
  return (
    <span className="ml-auto grid h-[22px] min-w-[22px] place-items-center rounded-[7px] bg-[#ddf5e6] px-1.5 text-xs font-semibold text-[#14653a] md:max-lg:hidden">
      {count}
    </span>
  );
}

function SideNavLink({ item, unread }: { item: NavItem; unread: number }) {
  return (
    <NavLink
      to={item.to}
      end={!item.prefix}
      title={item.label}
      className={({ isActive }) => cn(SIDE_LINK, isActive && "bg-brand text-white hover:bg-brand")}
    >
      <item.icon size={18} strokeWidth={1.5} aria-hidden="true" />
      <span className="md:max-lg:hidden">{item.label}</span>
      {item.badge && <CountBadge count={unread} />}
    </NavLink>
  );
}

function SideAction({
  icon: Icon,
  label,
  onClick,
}: {
  icon: LucideIcon;
  label: string;
  onClick?: () => void;
}) {
  const content = (
    <>
      <Icon size={18} strokeWidth={1.5} aria-hidden="true" />
      <span className="md:max-lg:hidden">{label}</span>
    </>
  );
  return onClick ? (
    <button type="button" title={label} onClick={onClick} className={cn(SIDE_LINK, "w-full")}>
      {content}
    </button>
  ) : (
    <Link to="/settings" title={label} className={SIDE_LINK}>
      {content}
    </Link>
  );
}

function Logo({ size = 36 }: { size?: number }) {
  return (
    <span
      style={{ width: size, height: size }}
      className="grid flex-none place-items-center rounded-[11px] bg-ink text-white"
    >
      <GraduationCap size={18} strokeWidth={1.5} aria-hidden="true" />
    </span>
  );
}

function TabBar({
  items,
  unread,
  onLogout,
}: {
  items: NavItem[];
  unread: number;
  onLogout: () => void;
}) {
  const [moreOpen, setMoreOpen] = useState(false);
  const more = items.filter((item) => !item.tab);
  const close = () => setMoreOpen(false);

  return (
    <>
      {moreOpen && (
        <div
          role="menu"
          className="fixed right-3 bottom-[84px] left-3 z-50 flex flex-col rounded-[20px] border border-line bg-white p-2 md:hidden"
        >
          {more.map((item) => (
            <Link key={item.to} to={item.to} role="menuitem" onClick={close} className={SIDE_LINK}>
              <item.icon size={18} strokeWidth={1.5} aria-hidden="true" />
              {item.label}
              {item.badge && <CountBadge count={unread} />}
            </Link>
          ))}
          <Link to="/settings" role="menuitem" onClick={close} className={SIDE_LINK}>
            <Settings size={18} strokeWidth={1.5} aria-hidden="true" />
            Ayarlar
          </Link>
          <button type="button" role="menuitem" onClick={onLogout} className={SIDE_LINK}>
            <LogOut size={18} strokeWidth={1.5} aria-hidden="true" />
            Çıkış yap
          </button>
        </div>
      )}
      <nav
        aria-label="Alt menü"
        className="fixed inset-x-0 bottom-0 z-40 grid grid-cols-5 border-t border-line bg-white px-2 pt-2 pb-[max(10px,env(safe-area-inset-bottom))] md:hidden"
      >
        {items
          .filter((item) => item.tab)
          .map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={!item.prefix}
              onClick={close}
              className={({ isActive }) =>
                cn(
                  "flex flex-col items-center gap-1 text-[11px] font-medium",
                  isActive ? "text-ink" : "text-[#4b5160]",
                )
              }
            >
              {({ isActive }) => (
                <>
                  <span
                    className={cn(
                      "grid h-[30px] w-14 place-items-center rounded-full",
                      isActive && "bg-brand text-white",
                    )}
                  >
                    <item.icon size={18} strokeWidth={1.5} aria-hidden="true" />
                  </span>
                  {item.label}
                </>
              )}
            </NavLink>
          ))}
        <button
          type="button"
          aria-expanded={moreOpen}
          onClick={() => setMoreOpen((open) => !open)}
          className="flex flex-col items-center gap-1 text-[11px] font-medium text-[#4b5160]"
        >
          <span className="grid h-[30px] w-14 place-items-center rounded-full">
            <Ellipsis size={18} strokeWidth={1.5} aria-hidden="true" />
          </span>
          Daha fazla
        </button>
      </nav>
    </>
  );
}

export function AppShell() {
  const actor = useActor();
  const logout = useLogout();
  const unread = useUnreadCount(!!actor?.roles.includes("student")).data ?? 0;
  const items = actor ? navForRoles(actor.roles, actor.id) : [];
  const home = actor && items.length > 0 ? items[0].to : "/";

  return (
    <div className="md:p-5">
      <div className="mx-auto min-h-screen max-w-[1520px] overflow-hidden bg-surface md:flex md:min-h-[calc(100vh-40px)] md:rounded-[24px]">
        {items.length > 0 && (
          <aside className="hidden shrink-0 flex-col border-r border-line px-3 py-6 md:flex md:w-[76px] lg:w-[232px] lg:px-5">
            <Link
              to={home}
              aria-label="Dershane"
              className="mb-8 flex items-center justify-center gap-3 lg:justify-start lg:px-1"
            >
              <Logo />
              <span className="hidden text-lg font-semibold tracking-tight lg:inline">
                Dershane
              </span>
            </Link>
            <nav aria-label="Ana menü" className="flex flex-col gap-1">
              {items.map((item) => (
                <SideNavLink key={item.to} item={item} unread={unread} />
              ))}
            </nav>
            <div className="mt-auto flex flex-col gap-1 border-t border-line pt-4">
              <SideAction icon={Settings} label="Ayarlar" />
              <SideAction icon={LogOut} label="Çıkış yap" onClick={logout} />
            </div>
          </aside>
        )}
        <main className="min-w-0 flex-1 px-4 pt-5 pb-28 md:px-8 md:pt-7 md:pb-10">
          <div className="mb-6 flex items-center justify-between md:hidden">
            <Link to={home} className="flex items-center gap-2.5">
              <Logo size={34} />
              <span className="text-[17px] font-semibold tracking-tight">Dershane</span>
            </Link>
            <HeaderActions />
          </div>
          <Outlet />
        </main>
      </div>
      {items.length > 0 && <TabBar items={items} unread={unread} onLogout={logout} />}
    </div>
  );
}
