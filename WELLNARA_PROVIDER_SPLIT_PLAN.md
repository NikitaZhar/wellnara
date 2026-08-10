# План: кабинет провайдера на отдельные страницы

Документ описывает разбиение единой страницы `/provider` (5 секций через JS) на
пять отдельных серверных страниц. Только план — код не пишется до утверждения.
Соответствует основному `WELLNARA_WORKING_AGREEMENT.md` (clean architecture, тонкие
контроллеры, масштаб) и `WELLNARA_UI_WORKING_AGREEMENT.md`.

Принцип: поведение и данные не теряются и не дублируются; каждая страница грузит
только свои данные; появляются настоящие URL; тяжёлый JS остаётся только там, где нужен.

---

## 1. Целевые маршруты

| Секция (сейчас `?section=`) | Новый маршрут (GET) | Шаблон |
|---|---|---|
| clients (по умолчанию) | `/provider/clients` | `provider-clients.html` |
| offerings | `/provider/offerings` | `provider-offerings.html` |
| provider-calendar | `/provider/appointments` | `provider-appointments.html` |
| calendar | `/provider/availability` | `provider-availability.html` |
| profile | `/provider/profile` | `provider-profile.html` |

Плюс: `GET /provider` остаётся и делает `redirect:/provider/clients` — чтобы старые
ссылки, закладки и дефолтный редирект после логина не ломались.

Уже отдельные и не меняются по сути: `/provider/clients/{id}/wallet`,
`/provider/offerings/{id}/edit` (только обновятся их back-ссылки, см. §6).

---

## 2. Бэкенд

### 2.1 `ProviderPageModelAssembler`
Сейчас `populate()` наполняет всё сразу. Разнести на пять публичных методов
(профиль и календарь уже выделены приватно — просто сделать точками входа):

- `populateClients(model, provider)` → `clients`, `providerName`
- `populateOfferings(model, provider)` → `offerings`, `providerName`
- `populateAppointments(model, provider)` → `appointments`, `confirmedAppointments`, `providerName`
- `populateProfile(model, provider)` → `providerLogin`, `providerEmail`, `profileFirstName`,
  `profileLastName`, `profilePhone`, `providerCurrency`, `supportedCurrencies`, `providerName`
- `populateAvailability(model, provider)` → `calendarForm`, `planningFrom`, `planningTo`,
  `today`, `calendarTerms`, `availabilityOverrides`, `providerName`

`providerName` нужен каждой странице (заголовок/приветствие) — вынести в маленький
общий приватный хелпер, вызываемый из каждого метода. Старый `populate()` можно
удалить после того, как исчезнут все его вызовы (см. 2.4).

### 2.2 `ProviderController`
Заменить один `GET /provider` на:

- `GET /provider` → `redirect:/provider/clients`
- `GET /provider/clients` → housekeeping* + перенос session-сообщений
  `clientInviteSuccessMessage`/`clientInviteError` + `populateClients` → `provider-clients`
- `GET /provider/offerings` → `populateOfferings` → `provider-offerings`
- `GET /provider/appointments` → housekeeping* + `populateAppointments` → `provider-appointments`
- `GET /provider/availability` → housekeeping* + `populateAvailability` → `provider-availability`
- `GET /provider/profile` → `populateProfile` → `provider-profile`

\* housekeeping — текущие вызовы в начале `showPage` (`deleteExpiredAvailabilityPeriods`,
`deleteExpiredAvailabilityOverrides`, `expireStaleAppointmentRequests`). Их нужно
привязать к релевантным страницам: истечение заявок — к `/provider/appointments`;
чистка доступности — к `/provider/availability`. Уточнить, не завязана ли на них
корректность других страниц (если да — вызывать шире).
Осознанное изменение поведения: сейчас эти чистки срабатывают при каждом заходе на
`/provider`; после привязки к конкретным страницам частота снизится. Для MVP приемлемо;
в идеале — вынести в планировщик (вне объёма этой задачи).

Session-сообщения об инвайте клиента логически принадлежат странице клиентов —
переносим их в `GET /provider/clients`.

### 2.3 Редиректы POST-обработчиков

| Контроллер | Было | Станет |
|---|---|---|
| `ProfileController` | `redirect:/provider?section=profile&profileUpdated` | `redirect:/provider/profile?profileUpdated` |
| `OfferingController` (создание) | `redirect:/provider?section=offerings` | `redirect:/provider/offerings` |
| `OfferingController` (edit) | `redirect:/provider?section=offerings` | `redirect:/provider/offerings` |
| `ProviderClientController` (delete) | `redirect:/provider` | `redirect:/provider/clients` |
| `ClientInvitationController` (invite) | `redirect:/provider` | `redirect:/provider/clients` |
| `ProviderAppointmentController` | `redirect:/provider?section=provider-calendar` | `redirect:/provider/appointments` |
| `ProviderCalendarController` | `redirect:/provider?section=calendar` | `redirect:/provider/availability` |
| `WalletController` (`CLIENTS_REDIRECT`) | `redirect:/provider?section=clients` | `redirect:/provider/clients` |

`WalletController.CLIENTS_REDIRECT` используется в двух местах: в `GET .../wallet`, когда
клиент не привязан к провайдеру, и в пути ошибки команды кошелька. Оба переводятся на
`/provider/clients`. (Само-редирект top-up/packages на `.../wallet` не меняется.)

### 2.4 Пути «ошибка валидации → рендер страницы» (тонкое место)
`populate()` на пути ошибки вызывают **четыре** контроллера, и все сейчас возвращают
view `"provider"`. После разбиения каждый рендерит свою страницу со своей моделью:

- `ProviderAppointmentController` → `populateAppointments` + `appointmentActionError`
  → `provider-appointments`.
- `ProviderCalendarController` → `populateAvailability` + `calendarForm`/`planningFrom`/
  `planningTo`/`calendarErrors` → `provider-availability`.
- `ProfileController` → `populateProfile` + сохранённые `profileFirstName/LastName/Phone` +
  `profileError` → `provider-profile`.
- `OfferingController.createOffering` → `populateOfferings` + `offeringError` → `provider-offerings`.

Отдельно: `OfferingController.updateOffering` при ошибке возвращает `offering-edit`
(со своей моделью) — это корректно, **менять не нужно**.

### 2.5 `SecurityConfig`
Изменений не требуется: `/provider/**` уже под `hasRole("PROVIDER")`, дефолтный
success-redirect на `/home` не затрагивается.

---

## 3. Шаблоны (UI)

### 3.1 Разбиение
`provider.html` разбивается на пять шаблонов (см. таблицу §1). Каждый:
- подключает шапку-фрагмент `header('Provider')` и общий фрагмент навигации (§3.2);
- содержит только свою секцию, оформленную по Sea Glass;
- секция Profile уже готова — `provider-profile-section.snippet.html` ложится в `provider-profile.html`.

### 3.2 Новый фрагмент навигации по разделам
`templates/fragments/provider-nav.html` — фрагмент `nav(active)` со ссылками:
Clients, Offerings, Appointments, Availability, Profile → новые маршруты; активный
пункт подсвечивается. Ставится на каждой из пяти страниц под шапкой (табы становятся
настоящими ссылками, а не JS-переключением).

### 3.3 Распределение JavaScript
Единый большой `<script>` из `provider.html` разносится:
- логика доступности (превью термов, стейджинг разовых изменений, лимиты дат,
  таймзона, fetch на `/provider/calendar/preview`) → только `provider-availability.html`;
- тумблеры «Reject/Confirm» и «Reschedule» на приёмах → только `provider-appointments.html`;
- функция `showSection` и весь роутинг по `?section=` в `DOMContentLoaded` — **удаляются**
  (не нужны при отдельных страницах).
Endpoint `/provider/calendar/preview` не трогаем.

### 3.4 Обновление ссылок
- `provider-home.html`: `@{/provider(section='clients')}` → `@{/provider/clients}` и т.д.
  (карточки и панели дашборда).
- Уже переоформленные `provider-client-wallet.html` и `offering-edit.html`: back/cancel-ссылки
  `@{/provider(section='clients')}` / `@{/provider(section='offerings')}` → новые маршруты.
- Старый `provider.html` удаляется после переноса всех секций.

### 3.5 Требования к шаблонам, вытекающие из тестов (см. §5a)
- `provider-availability.html` обязан выводить `calendarErrors` (блок ошибок из `provider.html`
  переносится дословно) — иначе падают content-тесты валидации календаря.
- `provider-clients.html` должен содержать заголовок «My clients», выводить `providerName`,
  список `clients` и session-сообщения инвайта (`clientInviteSuccessMessage`/`clientInviteError`).
- `provider-offerings.html` выводит имена офферингов (на них смотрит edge-cases тест).
- Если осознанно меняем текст (напр. убираем «Wellnara: Provider») — синхронно правим
  соответствующие ассерты (§5a), а не подгоняем шаблон под старую строку.

---

## 4. Данные по страницам (контроль «ничего не потеряно»)

- **Clients:** `clients` (+ session `clientInviteSuccessMessage`/`clientInviteError`), форма
  инвайта клиента, ссылки Wallet/Delete. Ничего из текущей секции не выпадает.
- **Offerings:** `offerings`, форма создания, ссылка на edit, `offeringError` (путь ошибки
  создания подтверждён — рендерит страницу офферингов, см. §2.4).
- **Appointments:** `appointments` (pending) + `confirmedAppointments` со всеми действиями
  (accept/reject/reschedule/cancel/complete/no-show/acknowledge), `appointmentActionError`.
- **Availability:** `calendarForm`, `planningFrom/To`, `today`, `calendarTerms`,
  `availabilityOverrides`, `calendarErrors`, превью.
- **Profile:** все поля профиля + пароль + `profileError` + `profileUpdated` (см. готовый блок).

---

## 5. Риски и тонкие места
1. **Availability** — самый тяжёлый JS; переносить целиком, проверить превью и Save.
2. **Пути ошибок** (§2.4) — легко забыть заменить возвращаемый view и модель.
3. **Housekeeping-вызовы** (§2.2) — не «потерять» истечение заявок/чистку доступности;
   привязать к правильным страницам или вызывать шире.
4. **Session-сообщения инвайта** — перенести именно на страницу клиентов.
5. **Старые ссылки** — оставить `/provider` → redirect, чтобы ничего внешнего не сломать.
6. **Тесты** — разбиение гарантированно ломает часть MVC-тестов; правки обязательны (§5a).

---

## 5a. Обновление существующих тестов (обязательная часть работ)

Разбиение меняет и редиректы, и то, что `GET /provider` теперь отдаёт 302 вместо
контента. Поэтому существующие MVC-тесты правятся предметно.

**Ассерты редиректов** (`redirectedUrl(...)`) — привести к новым маршрутам:

| Тест | Было (ассерт) | Станет |
|---|---|---|
| `AppointmentLifecycleMvcTest` (6 мест) | `/provider?section=provider-calendar` | `/provider/appointments` |
| `ProviderCalendarMvcTest` (2 места) | `/provider?section=calendar` | `/provider/availability` |
| `ProviderClientAndOfferingEdgeCasesMvcTest` | `/provider?section=offerings` и `/provider` | `/provider/offerings` и `/provider/clients` |
| `WalletViewMvcTest` | `/provider?section=clients` | `/provider/clients` |
| `ProviderClientFlowMvcTest` | `/provider` (delete/invite) | `/provider/clients` |
| `WalletAccessMvcTest` | проверяет доступ по `/provider/**` | маршруты не меняются; сверить ассерты |

**Вызовы `get("/provider")` (8 шт.) — перенацелить, но цель зависит от того, какой
контент проверяет тест** (после разбиения `/provider` отдаёт 302, а данные разных секций
теперь на разных страницах). Поимённая карта:

| Тест / место | Что проверяет | Перенацелить на |
|---|---|---|
| `ProviderClientFlowMvcTest` (флеш инвайта, список клиентов, «Email already used», проверка после удаления) | клиенты + сообщения инвайта | `/provider/clients` |
| `ProviderAdminFlowMvcTest` (2 места) | лендинг провайдера + имя | `/provider/clients` |
| `ProviderClientAndOfferingEdgeCasesMvcTest` (стр. ~186) | **имена офферингов** («Own Offering») | **`/provider/offerings`** |

Внимание: последний случай — исключение; вести его на clients нельзя, там нет офферингов.

**Content-ассерты, завязанные на СТАРЫЙ текст (ломаются даже при верном перенацеливании).**
Редизайн убирает часть текста, на который смотрят тесты — такие строки правятся под новый
UI (или скелет страницы обязан содержать эквивалент):

- `ProviderAdminFlowMvcTest`: `containsString("Wellnara: Provider")` — в новой шапке этой
  строки нет (там «Wellnara» + бейдж роли). Ассерт переписать под текущий текст страницы.
  `"Successful Provider"` (имя провайдера) — целевая страница (`/provider/clients`) обязана
  выводить `providerName`, иначе ассерт правится.
- `ProviderClientFlowMvcTest`: `"My clients"` — новая страница клиентов должна содержать этот
  заголовок, иначе ассерт правится. Строки-данные («Client Three», email, «Invitation sent
  to…», «Email already used») — это данные/сообщения, они сохраняются, если страница клиентов
  рендерит `clients` и session-сообщения инвайта.

**Content/model-ассерты на путях ошибок (URL и строки НЕ меняются, но есть зависимость от шаблона):**

- `ProviderCalendarMvcTest` (~9 тестов валидации): `post("/provider/calendar")` → `status().isOk()`
  → `content().string(containsString("…текст ошибки…"))`. Останутся зелёными **только если
  `provider-availability.html` выводит `calendarErrors` дословно** (блок вывода ошибок обязан
  переехать в шаблон доступности). URL POST не меняется.
- `AppointmentLifecycleMvcTest` (стр. 135–137): `status().isOk()` + `model().attribute("appointmentError", …)`.
  Переживёт без правок, но фиксирует требование: путь ошибки приёма возвращает 200 с рендером
  `provider-appointments`, а не редирект.

**Без изменений:** `WalletViewMvcTest` (`view().name("provider-client-wallet")` — не трогаем),
`HomeMvcTest` (`provider-home`/`client-home`), `WalletAccessMvcTest` (редирект на сам кошелёк
не меняется). Прямых ассертов `view().name("provider")` в тестах нет.

Общий принцип (из основного соглашения): если поведение меняется осознанно — тест
приводится в соответствие, а не обходится. После правок весь набор тестов должен быть зелёным.

---

## 6. Проверка / приёмка (ручной смоук)
- Каждая из 5 страниц открывается по своему URL, показывает свои данные, шапка и
  навигация одинаковые, активный пункт подсвечен.
- POST-действия ведут на правильную страницу: инвайт клиента → clients; создать/удалить
  оффер → offerings; действия по приёму → appointments; Save календаря → availability;
  Save профиля → profile (+ баннер «Profile updated»).
- Пути ошибок: невалидный календарь → страница availability с ошибками и сохранённым
  вводом; ошибка действия по приёму → страница appointments с сообщением.
- Превью доступности (`/provider/calendar/preview`) работает как раньше.
- `/provider` без секции редиректит на `/provider/clients`.
- Ссылки с Home, из кошелька и edit-оффера ведут на новые маршруты.
- Доступ под ролью PROVIDER; чужие роли — как раньше.
- Прогнать существующие тесты; если поведение менялось осознанно — тесты привести
  в соответствие (по основному соглашению).

---

## 7. Порядок работ
1. Бэкенд: разнести ассемблер → маршруты в `ProviderController` → редиректы (вкл. Wallet) → пути ошибок (все 4).
2. Временные «скелетные» шаблоны пяти страниц (чтобы всё поднялось и прошёл смоук).
3. Обновить существующие тесты (§5a) → прогнать набор до зелёного.
4. Оформление страниц по очереди (Sea Glass), переиспользуя готовые компоненты и Profile.
5. Удалить `provider.html`, `showSection` и мёртвые ссылки. Финальный смоук + повторный прогон тестов.

## 8. Откат
Изменения атомарны по коммитам (бэкенд отдельно от вёрстки). Быстрый откат — вернуть
`provider.html` + единый `GET /provider` + старые редиректы; новые шаблоны и фрагмент
навигации самодостаточны и не влияют на другие роли.

## 9. Вне объёма
Redis-сессии, actuator, изменение бизнес-логики приёмов/кошелька, переход на SPA,
доработки безопасности — не входят; выполняются на своих этапах.
