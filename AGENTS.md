# Инструкция для агентов: управление Visual Paradigm через плагин

Репозиторий содержит Java-плагин к Visual Paradigm 18.1 CE (Open API) с локальным HTTP-сервером внутри VP и CLI `bin/vp` к нему. Через них агент строит и правит UML-модели без кликов по экрану: создаёт элементы модели и диаграммы, сохраняет проект и рендерит диаграммы в PNG.

## Что где

| Путь | Что это |
|---|---|
| `plugin/src/ru/urfu/lab2/Server.java` | HTTP-сервер на 127.0.0.1 внутри VP, все вызовы API выполняются на EDT |
| `plugin/src/ru/urfu/lab2/LiveRunner.java` | компиляция и запуск Java-кода внутри VP «на лету» |
| `plugin/src/ru/urfu/lab2/B.java` | помощник: поиск и создание элементов, фигур, связей, лог |
| `plugin/src/ru/urfu/lab2/Seq.java` | построитель диаграмм последовательности |
| `plugin/src/ru/urfu/lab2/Stage*.java` | построение модели ЛР2 по пособию Малышко (пример большого сценария) |
| `plugin/plugin.xml`, `plugin/build.sh` | описание плагина, сборка и установка в VP |
| `bin/vp` | CLI к серверу |
| `examples/live/` | небольшие скрипты для `vp run`: обход модели, пробы API, экспорт картинок |
| `examples/lab2/` | сценарии пересборки проекта ЛР2 с нуля |

## Установка

```bash
plugin/build.sh
```

Собирает с `--release 8` против `openapi.jar` из `/Applications/Visual Paradigm CE.app` и копирует плагин в `~/Library/Application Support/VisualParadigm/plugins/ru.urfu.lab2`. После установки VP нужно перезапустить. При старте плагин запускает сервер и пишет порт и токен в `~/.vp_lab2_server.json`. Путь можно переопределить переменной `VP_SERVER_CONF`.

Перезапуск VP нужен только после правок `Server.java`, `LiveRunner.java`, `Lab2Plugin.java` или `plugin.xml`. Остальной код подхватывается через `vp run` без переустановки.

## Команды `bin/vp`

```text
vp status                      проект, файл, modified, активная диаграмма
vp diagrams                    список диаграмм (name / type / parent / id)
vp open FILE.vpp               открыть проект
vp save                        сохранить проект
vp revert                      переоткрыть файл без сохранения (сервер сам жмёт «No» в «save?»)
vp dialogs                     открытые диалоги VP: заголовок, тексты, кнопки
vp click BUTTON [TEXT]         нажать кнопку в диалоге, содержащем TEXT
vp show "Диаграмма"            открыть вкладку ("Родитель/Диаграмма" при совпадении имён)
vp inspect ["Диаграмма"]       фигуры диаграммы в JSON (shape, id, x/y/w/h, модель, геттеры)
vp export "Диаграмма" OUT.png  отрендерить диаграмму в PNG без снимка экрана
vp run FILE.java [--nosrc]     скомпилировать и выполнить код внутри VP
```

`vp export` и `vp show` берут первую диаграмму с таким именем. Если имена совпадают (например, диаграмма классов и диаграмма пакетов `BSAccess`), используйте `examples/live/ExportAll.java`: он различает тип и родителя.

## `vp run`: код внутри VP

Файл для `run` — класс в пакете `ru.urfu.lab2` с методом:

```java
package ru.urfu.lab2;
public class MyScript {
    public static Object run(B b) throws Exception {
        b.step("что-то построить", () -> { /* ... */ });
        return "ok";
    }
}
```

- Компилируется вместе со всем `plugin/src`, поэтому правки `B`, `Seq`, `Stage*` применяются сразу. С `--nosrc` компилируется только против установленного `lab2.jar`.
- Выполняется на EDT, ответ ждёт до 180 с. Каждый запуск идёт в новом ClassLoader, статика между запусками не сохраняется.
- `run` печатает результат, лог `B` (`b.log`), stdout и стек ошибки. Если не компилируется хоть один файл в `plugin/src`, падает весь запуск.
- `b.finish()` не вызывайте: лог и так возвращается в ответе.
- В JRE VP нет `jdk.zipfs`, поэтому javac работает с распакованными jar в `~/Library/Caches/lab2-live`. Это делается автоматически.

## Рабочий цикл

1. Правка кода (`plugin/src` или скрипт в `examples/`).
2. `vp run скрипт.java` — построение.
3. `vp save`, затем `vp revert` — сохранить и переоткрыть с диска.
4. `vp export ... .png` и посмотреть картинку.
5. Сверить с эталоном. Для объективности отдать пары картинок «эталон / результат» отдельному субагенту, не передавая ему свои выводы.

**Проверяйте только после save + revert.** Многое выглядит правильно в памяти редактора и ломается после перезагрузки файла: пропадают подписи, сообщения разлетаются, теряется членство в наборах обобщений. Не докладывайте «готово» по экспорту до сохранения.

## Правила работы с экраном и проектом

- Окно VP на передний план не выводить без нужды: всё делается через сервер. Если всё же пришлось управлять интерфейсом VP напрямую, после действий вернуть фокус исходному приложению и предупредить пользователя.
- `vp save` — только с согласия пользователя, если проект открыт им. `vp revert` и `vp open` сбрасывают несохранённые правки, поэтому перед ними проверьте `vp status` (`modified`). Если в VP могут быть ручные правки пользователя, спросите его.
- Перед перезаписью `.vpp` сделайте копию. Файл `.vpp` — это SQLite, его можно читать `sqlite3` для диагностики: таблицы `MODEL_ELEMENT`, `DIAGRAM`, `DIAGRAM_ELEMENT`.
- Старые шаблоны (например `vp162template.vpp`) при открытии просят конвертацию. `vp revert` отвечает «No», и проект открывается некорректно: VP переименовывает элементы в «Имя2». Такие файлы открывайте через `vp open` и следите за `vp dialogs`.

## Причуды Open API (проверено на VP 18.1)

- `IModelElement.duplicate()` неглубокий: копирует только сам элемент, без детей и без родителя. «Duplicate Recursively» придётся писать вручную.
- Обёртки элементов модели каждый раз новые: сравнивайте по `getId()`, а не через `==`.
- Направления связей: в обобщении `from` = общий, `to` = частный. В реализации `from` = поставщик (интерфейс, вариант использования), `to` = реализующий. В зависимости `from` = клиент.
- Подписи фигур и связей: `setRequestResetCaption(true)`.
- Ручной маршрут связи: `IConnectorUIModel.clearPoints()` + `addPoint(...)` после создания. Первая и последняя точки лежат на границах фигур, координаты абсолютные.
- Позиция подписи коннектора: через рефлексию `getCaptionUIModel()`, затем `getX()` и `setY(0)` (это запускает ленивую раскладку), затем `setBounds(...)`, `setVisible(true)`, `setRequestResetCaption(true)`. Делать после того, как созданы все коннекторы и диаграмма открыта: `openDiagram` раскладывает подписи заново.
- Скрыть члены класса на диаграмме: `IClassUIModel.setShowOperationType(OP_SHOW_TYPE_NONE)` и `setShowAttributeType(ATTR_SHOW_TYPE_NONE)`. `setHiddenOperationModels` не работает.
- `{unique}` у атрибутов: `getMultiplicityDetail()` или `createMultiplicity()`, затем `setUnique(false)`.
- Кратность параметра хранить как `typeModifier` `"[4]"`, иначе на диаграмме классов её не видно.
- Роли ассоциаций показывать явно: `IAssociationUIModel.setShowFromRoleName/ToRoleName(true)`. Стрелки двунаправленной ассоциации: `setShowNavigationArrows(SHOW_ALL_ARROWS_AND_CROSSES)`.
- `IGeneralizationSet`: после построения диаграмм заново вызвать `addGeneralization(g)`, VP теряет членство. Подпись набора VP рисует не всегда.
- Дорожки деятельности (`ActivitySwimlane2`): заголовки `ActivityPartitionHeader` и ячейки `ActivitySwimlane2Compartment` связываются по **id фигуры заголовка**.
- Call Behavior Action: `IActivityAction.setActionType(ICallBehaviorAction)` с `setBehavior(activity)`.

### Диаграммы последовательности (рецепт, переживающий перезагрузку)

- Стереотип класса на линию жизни не навешивается: `addStereotype("имя")` по имени. Актёр: `createInteractionActor` + `frame.addInteractionActor`.
- Фигуры `Activation` — верхнего уровня, не дочерние линии жизни. Коннектор сообщения соединяет **фигуры линий жизни**, связь с активацией задаётся только в модели.
- Сообщению-вызову операции имя не задавайте: VP сам печатает сигнатуру операции. Флаги `setShowMessageName`, `setShowMessageOperationSignature`, `setShowConnectorName` не трогайте: явный `showMessageName=1` прячет подписи после загрузки.
- Найденное или потерянное сообщение: фигура `dm.createDiagramElement(d, "LostFoundMessageEnd")` без элемента модели.
- `IInteractionConstraint.setMinint` на уже привязанном guard даёт NPE: создайте новый constraint, задайте min/max и только потом привяжите.

## Примеры

- `examples/live/Hello.java` — минимальный пример: только читает модель, ничего не меняет.
- `examples/lab2/Smoke.java` — строит классы этапа 2 и диаграмму Login Basic Flow, то есть **меняет** проект.
- `examples/live/Tree.java` — дерево модели в `~/Library/Caches/lab2-live/tree.txt`.
- `examples/live/Mech.java` — классы пакета с членами и связями.
- `examples/live/ExportAll.java` — экспорт списка диаграмм. Читает `~/Library/Caches/lab2-live/export.txt`: в первой строке папка, далее строки `файл.png|ТипДиаграммы|Родитель|Имя`, тип и родитель можно оставить пустыми.
- `examples/live/*Probe*.java`, `Cap2.java`, `FlowCap.java` — пробы API: подписи, ассоциации, наборы обобщений.
- `examples/lab2/Rebuild{1,2,3}.sh` — пересборка проекта ЛР2 по этапам. Папку проекта задаёт `LAB2_WORKSPACE`.

## Известные жёстко прописанные пути

- `Server.java` дублирует `vp_server.json` в папку проекта ЛР2 (константа `WORKSPACE`). Для другого проекта её стоит поменять или убрать, а затем пересобрать плагин и перезапустить VP.
- `plugin/build.sh` ищет VP в `/Applications/Visual Paradigm CE.app`.
