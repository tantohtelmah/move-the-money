#!/bin/bash

BASE_URL="http://localhost:8080"

while true; do
    echo ""
    echo "=============================="
    echo "       MOVE THE MONEY"
    echo "=============================="
    echo "1. Create account"
    echo "2. Check account balance"
    echo "3. Transfer money"
    echo "4. View transaction history"
    echo "5. Exit"
    echo "=============================="

    read -p "Choose an option: " choice

    case $choice in

        1)
            read -p "Starting balance: $" balance

            curl -s -X POST "$BASE_URL/accounts" \
                -H "Content-Type: application/json" \
                -d "{\"startingBalance\":$balance}"

            echo ""
            ;;

        2)
            read -p "Account ID: " accountId

            curl -s "$BASE_URL/accounts/$accountId"

            echo ""
            ;;

        3)
            read -p "From account ID: " fromId
            read -p "To account ID: " toId
            read -p "Amount: $" amount
            read -p "Idempotency key: " key

            curl -s -X POST "$BASE_URL/transfers" \
                -H "Content-Type: application/json" \
                -H "Idempotency-Key: $key" \
                -d "{
                    \"fromAccountId\":$fromId,
                    \"toAccountId\":$toId,
                    \"amount\":$amount
                }"

            echo ""
            ;;

        4)
            read -p "Account ID: " accountId

            curl -s "$BASE_URL/accounts/$accountId/transactions"

            echo ""
            ;;

        5)
            echo "Exiting demo."
            exit 0
            ;;

        *)
            echo "Invalid option."
            ;;
    esac
done