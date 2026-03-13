FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY backend ./backend
COPY frontend ./frontend
COPY storage ./storage

RUN if [ ! -d backend/out ]; then mkdir -p backend/out; fi \
    && javac -cp "backend/lib/*" -d backend/out backend/src/com/missingperson/App.java

ENV HOST=0.0.0.0
ENV PORT=10000
ENV APP_DATA_DIR=/var/data

EXPOSE 10000

CMD ["java", "-cp", "backend/out:backend/lib/*", "com.missingperson.App"]
