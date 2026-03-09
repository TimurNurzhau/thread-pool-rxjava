Курсовая работа. Часть 1: Custom ThreadPool
Обзор
Реализация собственного пула потоков с настраиваемыми параметрами, политиками отказа и балансировкой нагрузки.

Архитектура
Основные компоненты:
CustomThreadPool - основная реализация пула

ThreadPoolConfig - конфигурация параметров

Worker - рабочий поток с обработкой задач

CustomThreadFactory - фабрика потоков с логированием

RejectPolicy - интерфейс политик отказа

LoadBalancer - интерфейс балансировки

Параметры настройки:
ThreadPoolConfig config = new ThreadPoolConfig(2, 4, 5, TimeUnit.SECONDS, 5, 1);

Где параметры означают:

corePoolSize = 2 (базовое количество потоков)

maxPoolSize = 4 (максимальное количество потоков)

keepAliveTime = 5 секунд (время простоя)

queueSize = 5 (размер очереди)

minSpareThreads = 1 (минимальное число резервных потоков)

Политики отказа
1. AbortPolicy (по умолчанию)
   Просто отклоняет задачу с логированием ошибки. Лог: [Rejected] Task ... rejected due to overload!

2. CallerRunsPolicy
   Выполняет задачу в вызывающем потоке. Лог: [Rejected] Task ... executed in caller thread

3. DiscardPolicy
   Молча отбрасывает задачу без логирования ошибки. Лог: только предупреждение о переполнении

Балансировка Round Robin
Каждый Worker привязан к своей очереди

Задачи распределяются по кругу между очередями

При необходимости создаются новые очереди и Worker'ы

Тестирование производительности
Тест 1: Обычная работа (8 задач)
Параметры: core=2, max=4, queue=5
Результат: все задачи выполнены, создано 4 потока
Время выполнения: примерно 5 секунд

Тест 2: Переполнение (10 задач)
Параметры: core=2, max=4, queue=5
Результат: 9 задач выполнено, 1 отклонена (AbortPolicy)

Тест 3: CallerRunsPolicy
Параметры: core=1, max=1, queue=2
Результат: 4 задачи в пуле, 1 в main потоке

Тест 4: DiscardPolicy
Параметры: core=1, max=1, queue=2
Результат: 3 задачи выполнено, 2 отброшены

Тест 5: Round Robin балансировка
Параметры: core=3, max=5, queue=3
Результат: 10 задач равномерно распределены по 5 очередям

Сравнение с ThreadPoolExecutor
Основные отличия CustomThreadPool от стандартного ThreadPoolExecutor:

Настройка очередей: Round Robin против одной очереди

Политики отказа: 3 гибкие политики против 4 стандартных

Логирование: детальное всех событий против ограниченного

Наличие параметра minSpareThreads

Привязка Worker к очереди

Выводы
Реализация полностью соответствует требованиям

Успешно протестированы все сценарии

Балансировка Round Robin эффективно распределяет нагрузку

Политики отказа покрывают все возможные случаи

Структура кода
Пакет threadpool содержит следующие классы и интерфейсы:

CustomThreadPool.java - основная реализация

ThreadPoolConfig.java - конфигурация

executor/CustomExecutor.java - интерфейс пула

factory/CustomThreadFactory.java - фабрика потоков

policy/RejectPolicy.java - интерфейс политик отказа

policy/AbortPolicy.java - политика отклонения

policy/CallerRunsPolicy.java - политика выполнения в вызывающем потоке

policy/DiscardPolicy.java - политика молчаливого отбрасывания

queue/LoadBalancer.java - интерфейс балансировки

queue/RoundRobinBalancer.java - реализация Round Robin

worker/Worker.java - рабочий поток

Как запустить
Для запуска демонстрационной программы выполните команду:
mvn clean compile exec:java -Dexec.mainClass="com.example.demo.ThreadPoolDemo"