# vp-server

Плагин к Visual Paradigm 18.1 CE с локальным HTTP-сервером внутри VP и CLI `bin/vp`. Позволяет строить UML-модели кодом (Open API), выполнять Java-скрипты внутри VP без перезапуска, сохранять проект и рендерить диаграммы в PNG.

```bash
plugin/build.sh          # собрать и установить плагин, затем перезапустить VP
bin/vp status            # проверить связь с сервером
bin/vp run examples/live/Hello.java   # только читает модель
bin/vp export "Main" /tmp/main.png
```

Подробности для агентов и людей: [AGENTS.md](AGENTS.md).
