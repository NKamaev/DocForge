// data.jsx — sample Russian content
const SAMPLE_TEMPLATES = [
  { id: "tpl-rent", name: "Договор аренды нежилого помещения", category: "Договоры", status: "published", updated: "01.04.2026", author: "Соколова А.", count: 42 },
  { id: "tpl-app", name: "Заявление о приёме на работу", category: "Кадры", status: "draft", updated: "23.04.2026", author: "Иванов П.", count: 18 },
  { id: "tpl-order", name: "Приказ о предоставлении отпуска", category: "Кадры", status: "published", updated: "15.04.2026", author: "Иванов П.", count: 67 },
  { id: "tpl-nda", name: "Соглашение о неразглашении (NDA)", category: "Договоры", status: "published", updated: "12.04.2026", author: "Соколова А.", count: 33 },
  { id: "tpl-act", name: "Акт приёма-передачи имущества", category: "Договоры", status: "draft", updated: "20.04.2026", author: "Кузнецов В.", count: 9 },
  { id: "tpl-dov", name: "Доверенность на представление интересов", category: "Доверенности", status: "published", updated: "08.04.2026", author: "Соколова А.", count: 24 },
  { id: "tpl-fire", name: "Приказ об увольнении по собственному желанию", category: "Кадры", status: "draft", updated: "22.04.2026", author: "—", count: 0 },
  { id: "tpl-srv", name: "Договор оказания услуг", category: "Договоры", status: "published", updated: "30.03.2026", author: "Кузнецов В.", count: 51 },
  { id: "tpl-claim", name: "Претензия о возврате денежных средств", category: "Претензии", status: "draft", updated: "18.04.2026", author: "—", count: 2 },
];

const SAMPLE_DOCUMENTS = [
  { id: "d-1", folder: "f-rent", name: "Аренда офис · ООО «Карелия»", template: "Договор аренды нежилого помещения", updated: "24.04.2026 14:21", author: "Соколова А.", status: "in-progress", progress: 78 },
  { id: "d-2", folder: "f-hr-vac", name: "Отпуск Петров · 28.04 — 11.05", template: "Приказ о предоставлении отпуска", updated: "23.04.2026 11:02", author: "Иванов П.", status: "complete", progress: 100 },
  { id: "d-3", folder: "f-nda", name: "NDA · ИП Гордеев", template: "Соглашение о неразглашении (NDA)", updated: "22.04.2026 18:45", author: "Соколова А.", status: "complete", progress: 100 },
  { id: "d-4", folder: "f-acts", name: "Акт сдачи · склад №3", template: "Акт приёма-передачи имущества", updated: "20.04.2026 09:30", author: "Кузнецов В.", status: "in-progress", progress: 35 },
  { id: "d-5", folder: "f-hr-hire", name: "Заявление Сидорова", template: "Заявление о приёме на работу", updated: "19.04.2026 16:00", author: "Иванов П.", status: "complete", progress: 100 },
  { id: "d-6", folder: "f-services", name: "Услуги · веб-разработка КамАЗ", template: "Договор оказания услуг", updated: "15.04.2026 10:11", author: "Кузнецов В.", status: "in-progress", progress: 60 },
  { id: "d-7", folder: "f-rent", name: "Аренда склад · ИП Морозов", template: "Договор аренды нежилого помещения", updated: "14.04.2026 09:55", author: "Соколова А.", status: "complete", progress: 100 },
  { id: "d-8", folder: "f-hr-vac", name: "Отпуск Григорьевой", template: "Приказ о предоставлении отпуска", updated: "12.04.2026 17:20", author: "Иванов П.", status: "in-progress", progress: 45 },
  { id: "d-9", folder: "f-claims", name: "Претензия по поставке кабеля", template: "Претензия о возврате денежных средств", updated: "10.04.2026 11:30", author: "Кузнецов В.", status: "in-progress", progress: 22 },
  { id: "d-10", folder: "f-archive-2025", name: "Аренда офис · 2025 (продлён)", template: "Договор аренды нежилого помещения", updated: "30.12.2025 16:00", author: "Соколова А.", status: "complete", progress: 100 },
  { id: "d-11", folder: "f-archive-2025", name: "NDA · ООО «Феникс» 2025", template: "Соглашение о неразглашении (NDA)", updated: "18.11.2025 12:14", author: "Соколова А.", status: "complete", progress: 100 },
  { id: "d-12", folder: "root", name: "Доверенность Орлову", template: "Доверенность на представление интересов", updated: "23.04.2026 09:10", author: "Соколова А.", status: "complete", progress: 100 },
];

// Folder tree. parent === null → root level.
const SAMPLE_FOLDERS = [
  { id: "f-contracts", parent: null, name: "Договоры", color: 250 },
  { id: "f-rent", parent: "f-contracts", name: "Аренда" },
  { id: "f-services", parent: "f-contracts", name: "Услуги" },
  { id: "f-nda", parent: "f-contracts", name: "NDA" },
  { id: "f-acts", parent: "f-contracts", name: "Акты" },
  { id: "f-hr", parent: null, name: "Кадры", color: 30 },
  { id: "f-hr-hire", parent: "f-hr", name: "Приём" },
  { id: "f-hr-vac", parent: "f-hr", name: "Отпуска" },
  { id: "f-hr-fire", parent: "f-hr", name: "Увольнения" },
  { id: "f-claims", parent: null, name: "Претензии", color: 20 },
  { id: "f-powers", parent: null, name: "Доверенности", color: 280 },
  { id: "f-archive", parent: null, name: "Архив", color: 200 },
  { id: "f-archive-2025", parent: "f-archive", name: "2025 год" },
  { id: "f-archive-2024", parent: "f-archive", name: "2024 год" },
];

const SAMPLE_DIRECTORIES = [
  { id: "dir-cities", name: "Города", count: 145, fields: 4, updated: "12.03.2026" },
  { id: "dir-orgs", name: "Организации", count: 28, fields: 8, updated: "21.04.2026" },
  { id: "dir-staff", name: "Сотрудники", count: 64, fields: 6, updated: "23.04.2026" },
  { id: "dir-pos", name: "Должности", count: 32, fields: 3, updated: "01.04.2026" },
  { id: "dir-banks", name: "Банки", count: 18, fields: 5, updated: "10.03.2026" },
  { id: "dir-doctypes", name: "Документы, удостоверяющие личность", count: 6, fields: 2, updated: "01.01.2026" },
];

const RENT_PARAMS = [
  { code: "landlord_name", label: "Наименование Арендодателя", type: "Текст", required: true },
  { code: "landlord_inn", label: "ИНН Арендодателя", type: "Число", required: true },
  { code: "tenant_name", label: "Наименование Арендатора", type: "Текст", required: true },
  { code: "tenant_repr", label: "ФИО представителя Арендатора", type: "Текст", required: true },
  { code: "premise_addr", label: "Адрес помещения", type: "Текст", required: true },
  { code: "premise_area", label: "Площадь, кв.м", type: "Число", required: true },
  { code: "rent_amount", label: "Размер арендной платы, ₽/мес.", type: "Деньги", required: true },
  { code: "term_months", label: "Срок аренды (мес.)", type: "Число", required: true },
  { code: "start_date", label: "Дата начала аренды", type: "Дата", required: true },
  { code: "city", label: "Город заключения", type: "Справочник", required: true },
  { code: "with_furniture", label: "С мебелью?", type: "Да/Нет", required: false },
  { code: "deposit_months", label: "Залог (в месяцах аренды)", type: "Число", required: false },
];

const RENT_QUESTIONS = [
  { id: "q1", page: 1, label: "Кто Арендодатель?", help: "Введите полное юридическое наименование", param: "landlord_name", type: "text", value: "ООО «Северные окна»" },
  { id: "q2", page: 1, label: "ИНН Арендодателя", param: "landlord_inn", type: "text", value: "7806543210" },
  { id: "q3", page: 1, label: "Кто Арендатор?", param: "tenant_name", type: "text", value: "ООО «Карелия-Трейд»" },
  { id: "q4", page: 1, label: "ФИО представителя со стороны Арендатора", param: "tenant_repr", type: "text", value: "Орлов Дмитрий Сергеевич" },
  { id: "q5", page: 2, label: "Адрес арендуемого помещения", param: "premise_addr", type: "text", value: "г. Санкт-Петербург, ул. Гороховая, д. 47, помещ. 12-Н" },
  { id: "q6", page: 2, label: "Площадь, кв.м", param: "premise_area", type: "number", value: "84.5" },
  { id: "q7", page: 2, label: "Помещение передаётся с мебелью?", param: "with_furniture", type: "bool", value: "yes" },
  { id: "q8", page: 3, label: "Размер арендной платы, ₽/мес.", param: "rent_amount", type: "money", value: "180 000" },
  { id: "q9", page: 3, label: "Срок аренды (месяцев)", param: "term_months", type: "number", value: "11" },
  { id: "q10", page: 3, label: "Дата начала аренды", param: "start_date", type: "date", value: "01.05.2026" },
  { id: "q11", page: 3, label: "Залог в месяцах аренды", param: "deposit_months", type: "number", value: "2" },
  { id: "q12", page: 4, label: "Город заключения", param: "city", type: "select", value: "Санкт-Петербург", options: ["Москва", "Санкт-Петербург", "Казань", "Новосибирск"] },
];

const QUESTIONNAIRE_PAGES = [
  { i: 1, t: "Стороны" },
  { i: 2, t: "Объект" },
  { i: 3, t: "Условия" },
  { i: 4, t: "Подписание" },
];

const PARTICIPANTS = [
  { name: "Анна С.", initials: "АС", hue: 280 },
  { name: "Павел И.", initials: "ПИ", hue: 30 },
  { name: "Виктор К.", initials: "ВК", hue: 160 },
];

const REVISIONS = [
  { id: "r1", when: "Сегодня, 14:21", who: "Анна С.", note: "Уточнила реквизиты Арендатора" },
  { id: "r2", when: "Сегодня, 12:08", who: "Павел И.", note: "Изменил срок аренды на 11 месяцев" },
  { id: "r3", when: "Вчера, 18:42", who: "Анна С.", note: "Создан документ" },
];

const NAV_ITEMS = [
  { key: "home", label: "Главная", icon: "home" },
  { key: "documents", label: "Мои документы", icon: "doc", count: 12 },
  { key: "templates", label: "Шаблоны", icon: "templates", count: 9 },
  { key: "directories", label: "Справочники", icon: "book" },
];
