FROM rabbitmq:4.2-management

RUN apt-get update && \
    apt-get install -y wget unzip && \
    wget https://github.com/rabbitmq/rabbitmq-delayed-message-exchange/releases/download/v4.2.0/rabbitmq_delayed_message_exchange-4.2.0.ez -P /opt/rabbitmq/plugins/ && \
    rabbitmq-plugins enable rabbitmq_delayed_message_exchange && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*
